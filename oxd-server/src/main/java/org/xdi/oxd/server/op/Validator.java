package org.xdi.oxd.server.op;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xdi.oxauth.client.OpenIdConfigurationResponse;
import org.xdi.oxauth.model.crypto.signature.RSAPublicKey;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithm;
import org.xdi.oxauth.model.jws.RSASigner;
import org.xdi.oxauth.model.jwt.Jwt;
import org.xdi.oxauth.model.jwt.JwtClaimName;
import org.xdi.oxauth.model.jwt.JwtHeaderName;
import org.xdi.oxd.common.ErrorResponseCode;
import org.xdi.oxd.common.ErrorResponseException;
import org.xdi.oxd.server.service.PublicOpKeyService;
import org.xdi.oxd.server.service.StateService;

import java.util.Date;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 14/03/2017
 */

public class Validator {

    private static final Logger LOG = LoggerFactory.getLogger(Validator.class);

    private final Jwt idToken;
    private final OpenIdConfigurationResponse discoveryResponse;
    private final PublicOpKeyService keyService;
    private RSASigner rsaSigner;

    public Validator(Jwt idToken, OpenIdConfigurationResponse discoveryResponse, PublicOpKeyService keyService) {
        Preconditions.checkNotNull(idToken);
        Preconditions.checkNotNull(discoveryResponse);

        this.idToken = idToken;
        this.discoveryResponse = discoveryResponse;
        this.keyService = keyService;
        this.rsaSigner = createRSASigner(idToken, discoveryResponse, keyService);
    }

    public void validateAccessToken(String accessToken) {
        if (!Strings.isNullOrEmpty(accessToken)) {
            if (!rsaSigner.validateAccessToken(accessToken, idToken)) {
                throw new ErrorResponseException(ErrorResponseCode.INVALID_ACCESS_TOKEN_BAD_HASH);
            }
        }
    }

    public void validateAuthorizationCode(String code) {
        if (!Strings.isNullOrEmpty(code)) {
            if (!rsaSigner.validateAuthorizationCode(code, idToken)) {
                throw new ErrorResponseException(ErrorResponseCode.INVALID_AUTHORIZATION_CODE_BAD_HASH);
            }
        }
    }

    public static RSASigner createRSASigner(Jwt jwt, OpenIdConfigurationResponse discoveryResponse, PublicOpKeyService keyService) {
        final String jwkUrl = discoveryResponse.getJwksUri();
        final String kid = jwt.getHeader().getClaimAsString(JwtHeaderName.KEY_ID);
        final String algorithm = jwt.getHeader().getClaimAsString(JwtHeaderName.ALGORITHM);
        final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.fromString(algorithm);

        final RSAPublicKey publicKey = keyService.getRSAPublicKey(jwkUrl, kid);
        return new RSASigner(signatureAlgorithm, publicKey);
    }

    public void validateNonce(StateService stateService) {
        final String nonceFromToken = idToken.getClaims().getClaimAsString(JwtClaimName.NONCE);
        if (!stateService.isNonceValid(nonceFromToken)) {
            throw new ErrorResponseException(ErrorResponseCode.INVALID_NONCE);
        }
    }

    public boolean isIdTokenValid(String clientId) {
        try {
            validateIdToken(clientId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void validateIdToken(String clientId) {
        validateIdToken(null, clientId);
    }

    public void validateIdToken(String nonce, String clientId) {
        try {
            final String issuer = idToken.getClaims().getClaimAsString(JwtClaimName.ISSUER);
            final String nonceFromToken = idToken.getClaims().getClaimAsString(JwtClaimName.NONCE);
            final String audienceFromToken = idToken.getClaims().getClaimAsString(JwtClaimName.AUDIENCE);

            if (!Strings.isNullOrEmpty(nonce) && !nonceFromToken.endsWith(nonce)) {
                LOG.error("ID Token has invalid nonce. Expected nonce: " + nonce + ", nonce from token is: " + nonceFromToken);
                throw new ErrorResponseException(ErrorResponseCode.INVALID_ID_TOKEN_BAD_NONCE);
            }

            if (!clientId.equalsIgnoreCase(audienceFromToken)) {
                LOG.error("ID Token has invalid audience. Expected audience: " + clientId + ", audience from token is: " + audienceFromToken);
                throw new ErrorResponseException(ErrorResponseCode.INVALID_ID_TOKEN_BAD_AUDIENCE);
            }

            final Date expiresAt = idToken.getClaims().getClaimAsDate(JwtClaimName.EXPIRATION_TIME);
            final Date now = new Date();
            if (now.after(expiresAt)) {
                LOG.error("ID Token is expired. (It is after " + now + ").");
                throw new ErrorResponseException(ErrorResponseCode.INVALID_ID_TOKEN_EXPIRED);
            }

            // 1. validate issuer
            if (!issuer.equals(discoveryResponse.getIssuer())) {
                LOG.error("ID Token issuer is invalid. Token issuer: " + issuer + ", discovery issuer: " + discoveryResponse.getIssuer());
                throw new ErrorResponseException(ErrorResponseCode.INVALID_ID_TOKEN_BAD_ISSUER);
            }

            // 2. validate signature
            boolean signature = rsaSigner.validate(idToken);
            if (!signature) {
                final String jwkUrl = discoveryResponse.getJwksUri();
                final String kid = idToken.getHeader().getClaimAsString(JwtHeaderName.KEY_ID);

                keyService.refetchKey(jwkUrl, kid);

                RSASigner signerWithRefreshedKey = createRSASigner(idToken, discoveryResponse, keyService);
                signature = signerWithRefreshedKey.validate(idToken);

                if (!signature) {
                    LOG.error("ID Token signature is invalid.");
                    throw new ErrorResponseException(ErrorResponseCode.INVALID_ID_TOKEN_BAD_SIGNATURE);
                } else {
                    this.rsaSigner = signerWithRefreshedKey;
                }
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new ErrorResponseException(ErrorResponseCode.INVALID_ID_TOKEN_UNKNOWN);
        }
    }

    public Jwt getIdToken() {
        return idToken;
    }
}
