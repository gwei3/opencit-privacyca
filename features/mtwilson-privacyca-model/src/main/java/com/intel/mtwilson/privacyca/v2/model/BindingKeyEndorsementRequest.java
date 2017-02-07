/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.privacyca.v2.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ssbangal
 */
@JacksonXmlRootElement(localName="binding_key_endorsement_request")
public class BindingKeyEndorsementRequest{
    
    Logger log = LoggerFactory.getLogger(getClass().getName());
    
    private byte[] publicKeyModulus;
    private byte[] tpmCertifyKey;
    private byte[] tpmCertifyKeySignature;
    private byte[] aikDerCertificate;
    private short encryptionScheme; // TPM Encryption Scheme
    private byte[] nameDigest;
    private String tpmVersion = null;

    public short getEncryptionScheme() {
        return encryptionScheme;
    }

    public void setEncryptionScheme(short encryptionScheme) {
        this.encryptionScheme = encryptionScheme;
    }

    public byte[] getNameDigest() {
        return nameDigest;
    }

    public void setNameDigest(byte[] digest) {
        this.nameDigest = digest;
    }

    public byte[] getPublicKeyModulus() {
        return publicKeyModulus;
    }

    public void setPublicKeyModulus(byte[] publicKeyModulus) {
        this.publicKeyModulus = publicKeyModulus;
    }

    public byte[] getTpmCertifyKey() {
        return tpmCertifyKey;
    }

    public void setTpmCertifyKey(byte[] tpmCertifyKey) {
        this.tpmCertifyKey = tpmCertifyKey;
    }    

    public byte[] getTpmCertifyKeySignature() {
        return tpmCertifyKeySignature;
    }

    public void setTpmCertifyKeySignature(byte[] tpmCertifyKeySignature) {
        this.tpmCertifyKeySignature = tpmCertifyKeySignature;
    }

    public String getTpmVersion(){
        return tpmVersion;
    }
    
    public void setTpmVersion(String version){
        this.tpmVersion=version;
    }
    
    public byte[] getAikDerCertificate() {
        return aikDerCertificate;
    }

    public void setAikDerCertificate(byte[] aikDerCertificate) {
        this.aikDerCertificate = aikDerCertificate;
    }

    
}
