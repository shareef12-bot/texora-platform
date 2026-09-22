package com.texora.secops.sso.authn.controller;

import com.texora.secops.sso.authn.model.LoginResult;
import com.texora.secops.sso.authn.service.LoginOrchestrationService;
import com.texora.secops.sso.domain.SsoApplication;
import com.texora.secops.sso.repository.SsoApplicationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * This is what point 2 of the integration gap asked for: the ACTUAL
 * authentication step of /oauth2/authorize goes through
 * {@link LoginOrchestrationService#login}, not Spring Security's default
 * form-login username/password check.
 *
 * Flow: the Authorization Server's own filter chain redirects an
 * unauthenticated /oauth2/authorize request here
 * (AuthorizationServerConfig's LoginUrlAuthenticationEntryPoint), saving the
 * original request via the standard HttpSessionRequestCache. This
 * controller reads the client_id off that saved request to know which
 * application is being logged into, renders a form, and on submit calls
 * LoginOrchestrationService.login() directly. On success it manually
 * populates the SecurityContext (there is no AuthenticationProvider /
 * AuthenticationManager in this path — LoginOrchestrationService IS the
 * authentication decision) and redirects back into the saved
 * /oauth2/authorize request, which the Authorization Server then completes
 * normally, issuing a real authorization code.
 */
@Controller
public class LoginController {

    private final LoginOrchestrationService loginOrchestrationService;
    private final SsoApplicationRepository applicationRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public LoginController(LoginOrchestrationService loginOrchestrationService,
                            SsoApplicationRepository applicationRepository) {
        this.loginOrchestrationService = loginOrchestrationService;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/login")
    public String loginForm(HttpServletRequest request, HttpServletResponse response,
                             @RequestParam(required = false) String error, Model model) {
        String clientId = extractClientId(request, response);
        UUID applicationId = resolveApplicationId(clientId);

        model.addAttribute("clientId", clientId);
        model.addAttribute("applicationId", applicationId);
        model.addAttribute("error", error);
        return "login";
    }

    @PostMapping("/login")
    public void submit(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String mfaCode,
                        @RequestParam UUID applicationId,
                        HttpServletRequest request,
                        HttpServletResponse response) throws IOException {

        LoginResult result = loginOrchestrationService.login(username, password, applicationId, mfaCode);

        if (!result.isSuccess()) {
            response.sendRedirect("/login?error=" + result.getOutcome().name());
            return;
        }

        // LoginOrchestrationService's success/denial decision IS the authentication
        // decision for this session — we set the SecurityContext directly rather
        // than delegating to an AuthenticationManager/AuthenticationProvider chain.
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                result.getDcUserId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        String redirectUrl = savedRequest != null ? savedRequest.getRedirectUrl() : "/";
        response.sendRedirect(redirectUrl);
    }

    private String extractClientId(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) {
            return null;
        }
        String[] values = savedRequest.getParameterValues("client_id");
        return (values != null && values.length > 0) ? values[0] : null;
    }

    private UUID resolveApplicationId(String clientId) {
        if (clientId == null) {
            return null;
        }
        return applicationRepository.findByClientId(clientId).map(SsoApplication::getId).orElse(null);
    }
}
