package com.testdroid

import com.testdroid.TestDroidExtension.Authorization
import static java.lang.Boolean.*;


/**
 * @author Damian Sniezek <damian.sniezek@bitbar.com>
 */
enum APIClientType {

    APIKEY_PROXY_CREDENTIALS,
    APIKEY_PROXY,
    APIKEY,
    UNSUPPORTED

    public static Map<Tuple, APIClientType> mapping = new HashMap<>()

    static {
        mapping.put(new Tuple(Authorization.APIKEY, TRUE, TRUE), APIKEY_PROXY_CREDENTIALS)
        mapping.put(new Tuple(Authorization.APIKEY, TRUE, FALSE), APIKEY_PROXY)
        mapping.put(new Tuple(Authorization.APIKEY, FALSE, FALSE), APIKEY)
        mapping.put(new Tuple(Authorization.APIKEY, FALSE, TRUE), UNSUPPORTED)
    }

    static APIClientType resolveAPIClientType(Authorization authorization, Boolean useProxy, Boolean useProxyCredentials) {
        return mapping.get(new Tuple(authorization, useProxy, useProxyCredentials))
    }
}

