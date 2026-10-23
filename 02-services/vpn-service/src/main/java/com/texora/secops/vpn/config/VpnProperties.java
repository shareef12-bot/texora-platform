package com.texora.secops.vpn.config;

public class VpnProperties {

    private SecProperties sec = new SecProperties();

    private DcProperties dc = new DcProperties();

    private SessionProperties session = new SessionProperties();

    private DataplaneProperties dataplane = new DataplaneProperties();

    private MfaProperties mfa = new MfaProperties();

    private AuditProperties audit = new AuditProperties();

    private GatewayProperties gateway = new GatewayProperties();

    private SchedulingProperties scheduling = new SchedulingProperties();

    public VpnProperties() {
    }

    public SecProperties getSec() {
        return sec;
    }

    public void setSec(SecProperties sec) {
        this.sec = sec;
    }

    public DcProperties getDc() {
        return dc;
    }

    public void setDc(DcProperties dc) {
        this.dc = dc;
    }

    public SessionProperties getSession() {
        return session;
    }

    public void setSession(SessionProperties session) {
        this.session = session;
    }

    public DataplaneProperties getDataplane() {
        return dataplane;
    }

    public void setDataplane(DataplaneProperties dataplane) {
        this.dataplane = dataplane;
    }

    public MfaProperties getMfa() {
        return mfa;
    }

    public void setMfa(MfaProperties mfa) {
        this.mfa = mfa;
    }

    public AuditProperties getAudit() {
        return audit;
    }

    public void setAudit(AuditProperties audit) {
        this.audit = audit;
    }

    public GatewayProperties getGateway() {
        return gateway;
    }

    public void setGateway(GatewayProperties gateway) {
        this.gateway = gateway;
    }

    public SchedulingProperties getScheduling() {
        return scheduling;
    }

    public void setScheduling(SchedulingProperties scheduling) {
        this.scheduling = scheduling;
    }

}
