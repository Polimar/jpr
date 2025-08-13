package com.example.profileremover.core.model;

public class AppSettings {
    public String pstoolsDir = "C\\PSTools";
    public int connectTimeoutSec = 30;
    public int execTimeoutSec = 180;
    public String ldapHost = "";
    public int ldapPort = 636;
    public String ldapBindDn = "";
    public String ldapPassword = "";
    public String baseDn = "";
    public String displayAttr = "displayName";
    public String[] allowedHosts = new String[]{};
}


