package org.xdi.oxd.common.response;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 31/03/2017
 */

public class GetClientTokenResponse implements IOpResponse {

    @JsonProperty(value = "access_token")
    private String accessToken;
    @JsonProperty(value = "expires_in")
    private int expiresIn; // expiration time in seconds
    @JsonProperty(value = "refresh_token")
    private String refreshToken;
    @JsonProperty(value = "scope")
    private String scope;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    @Override
    public String toString() {
        return "GetClientTokenResponse{" +
                "accessToken='" + accessToken + '\'' +
                ", expiresIn=" + expiresIn +
                ", refreshToken=" + refreshToken +
                ", scope=" + scope +
                '}';
    }
}
