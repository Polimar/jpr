package com.example.profileremover.core.service;

import com.unboundid.ldap.sdk.*;

public class LdapService implements AutoCloseable {
    private final LDAPConnection connection;

    public LdapService(String host, int port, String bindDn, String password) throws LDAPException {
        this.connection = new LDAPConnection(host, port, bindDn, password);
    }

    public String findDisplayNameBySam(String baseDn, String samAccountName, String displayAttr) throws LDAPSearchException {
        String filter = "(&(objectClass=user)(sAMAccountName=" + samAccountName + "))";
        SearchRequest req = new SearchRequest(baseDn, SearchScope.SUB, filter, displayAttr);
        SearchResult res = connection.search(req);
        if (res.getEntryCount() == 0) return samAccountName;
        return res.getSearchEntries().get(0).getAttributeValue(displayAttr);
    }

    @Override
    public void close() {
        if (connection != null) connection.close();
    }
}


