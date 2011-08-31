/*
 * Este fichero forma parte del Cliente @firma.
 * El Cliente @firma es un aplicativo de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde www.ctt.map.es.
 * Copyright 2009,2010,2011 Gobierno de Espana
 * Este fichero se distribuye bajo  bajo licencia GPL version 2  segun las
 * condiciones que figuran en el fichero 'licence' que se acompana. Si se distribuyera este
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 */

package es.gob.afirma.signers.aobinarysignhelper;

import static es.gob.afirma.signers.aobinarysignhelper.SigUtils.createBerSetFromList;
import static es.gob.afirma.signers.aobinarysignhelper.SigUtils.getAttributeSet;
import static es.gob.afirma.signers.aobinarysignhelper.SigUtils.makeAlgId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.BERConstructedOctetString;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerIdentifier;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.TBSCertificateStructure;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.ietf.jgss.Oid;

import sun.security.x509.AlgorithmId;
import es.gob.afirma.exceptions.AOException;
import es.gob.afirma.misc.AOCryptoUtil;

/** Clase que implementa firma digital CMS Advanced Electronic Signatures
 * (CAdES).
 * La implementaci&oacute;n es la misma que para el Signed Data de CMS, salvo
 * que en los atributos del SignerInfo en vez de ir el n&uacute;mero de serie
 * (SerialNumber), va la firma del certificado.<br>
 * La Estructura del mensaje es la siguiente (Se omite la parte correspondiente
 * a CMS):<br>
 *
 * <pre>
 * <code>
 *  id-aa-ets-sigPolicyId OBJECT IDENTIFIER ::= { iso(1)
 *      member-body(2) us(840) rsadsi(113549) pkcs(1) pkcs9(9)
 *      smime(16) id-aa(2) 15 }
 *
 *      SignaturePolicyIdentifier ::= CHOICE {
 *           signaturePolicyId          SignaturePolicyId,
 *           signaturePolicyImplied     SignaturePolicyImplied
 *                                      -- not used in this version
 *   }
 *
 *      SignaturePolicyId ::= SEQUENCE {
 *           sigPolicyId           SigPolicyId,
 *           sigPolicyHash         SigPolicyHash,
 *           sigPolicyQualifiers   SEQUENCE SIZE (1..MAX) OF
 *                                   SigPolicyQualifierInfo OPTIONAL}
 * </code>
 * </pre>
 *
 * La implementaci&oacute;n del c&oacute;digo ha seguido los pasos necesarios
 * para crear un mensaje SignedData de BouncyCastle: <a
 * href="http://www.bouncycastle.org/">www.bouncycastle.org</a> */

public final class GenCadesEPESSignedData {

    private ASN1Set signedAttr2;

    /** M&eacute;odo que genera una firma digital usando el sitema conocido como
     * SignedData y que podr&aacute; ser con el contenido del fichero codificado
     * o s&oacute;lo como referencia del fichero.
     * @param parameters
     *        Par&aacute;metros necesarios para obtener los datos de
     *        SignedData.
     * @param omitContent
     *        Par&aacute;metro qeu indica si en la firma va el contenido del
     *        fichero o s&oacute;lo va de forma referenciada.
     * @param policy
     *        Url de la Politica aplicada.
     * @param qualifier
     *        OID de la pol&iacute;tica.
     * @param signingCertificateV2
     *        <code>true</code> si se desea usar la versi&oacute;n 2 del
     *        atributo <i>Signing Certificate</i> <code>false</code> para
     *        usar la versi&oacute;n 1
     * @param dataType
     *        Identifica el tipo del contenido a firmar.
     * @param keyEntry
     *        Entrada a la clave privada para firma.
     * @param messageDigest
     *        Hash a aplicar en la firma.
     * @return La firma generada codificada.
     * @throws java.security.NoSuchAlgorithmException
     *         Si no se soporta alguno de los algoritmos de firma o huella
     *         digital
     * @throws java.security.cert.CertificateException
     *         Si se produce alguna excepci&oacute;n con los certificados de
     *         firma.
     * @throws java.io.IOException
     *         Si ocurre alg&uacute;n problema leyendo o escribiendo los
     *         datos
     * @throws AOException
     *         Cuando ocurre un error durante el proceso de descifrado
     *         (formato o clave incorrecto,...) */
    public byte[] generateSignedData(final P7ContentSignerParameters parameters,
                                     final boolean omitContent,
                                     final String policy,
                                     final Oid qualifier,
                                     final boolean signingCertificateV2,
                                     final Oid dataType,
                                     final PrivateKeyEntry keyEntry,
                                     final byte[] messageDigest) throws NoSuchAlgorithmException, CertificateException, IOException, AOException {

        if (parameters == null) {
            throw new IllegalArgumentException("Los parametros no pueden ser nulos");
        }


        
        // ALGORITMO DE HUELLA DIGITAL

        AlgorithmIdentifier digestAlgorithmOID;

        final String signatureAlgorithm = parameters.getSignatureAlgorithm();
        String digestAlgorithmName = null;
        String keyAlgorithm = null;
        final int with = signatureAlgorithm.indexOf("with");
        if (with > 0) {
            digestAlgorithmName = AOCryptoUtil.getDigestAlgorithmName(signatureAlgorithm);
            final int and = signatureAlgorithm.indexOf("and", with + 4);
            if (and > 0) {
                keyAlgorithm = signatureAlgorithm.substring(with + 4, and);
            }
            else {
                keyAlgorithm = signatureAlgorithm.substring(with + 4);
            }
        }

        final AlgorithmId digestAlgorithmId = AlgorithmId.get(digestAlgorithmName);

        try {
            digestAlgorithmOID = makeAlgId(digestAlgorithmId.getOID().toString(), digestAlgorithmId.getEncodedParams());
        }
        catch (final Exception e) {
            throw new IOException("Error de codificacion: " + e);
        }

        // // ATRIBUTOS

        final X509Certificate[] signerCertificateChain = parameters.getSignerCertificateChain();
        final ASN1EncodableVector contextExcepcific =
                Utils.generateSignerInfo(signerCertificateChain[0],
                                         digestAlgorithmId,
                                         digestAlgorithmName,
                                         digestAlgorithmOID,
                                         parameters.getContent(),
                                         policy,
                                         qualifier,
                                         signingCertificateV2,
                                         dataType,
                                         messageDigest);
        
        signedAttr2                    = getAttributeSet(new AttributeTable(contextExcepcific));
        final ASN1Set signedAttributes = getAttributeSet(new AttributeTable(contextExcepcific));

        // Firma PKCS#1
        
        final ASN1OctetString encodedPKCS1Signature;
        try {
            encodedPKCS1Signature = firma(signatureAlgorithm, keyEntry);
        }
        catch (final AOException ex) {
            throw ex;
        }

        
        
        
        // ContentInfo
        ContentInfo contentInfo = null;
        final ASN1ObjectIdentifier contentTypeOID = new ASN1ObjectIdentifier(dataType.toString());

        if (omitContent == false) {
            final ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            final byte[] content = parameters.getContent();
            final CMSProcessable msg = new CMSProcessableByteArray(content);
            try {
                msg.write(bOut);
            }
            catch (final Exception ex) {
                throw new IOException("Error en la escritura del procesable CMS: " + ex);
            }
            contentInfo = new ContentInfo(contentTypeOID, new BERConstructedOctetString(bOut.toByteArray()));
        }
        else {
            contentInfo = new ContentInfo(contentTypeOID, null);
        }

        final TBSCertificateStructure tbsCertificateStructure = TBSCertificateStructure.getInstance(ASN1Object.fromByteArray(signerCertificateChain[0].getTBSCertificate()));
        final IssuerAndSerialNumber issuerAndSerialNumber = new IssuerAndSerialNumber(X500Name.getInstance(tbsCertificateStructure.getIssuer()), tbsCertificateStructure.getSerialNumber().getValue());

        final SignerIdentifier signerIdentifier = new SignerIdentifier(issuerAndSerialNumber);

        // AlgorithmIdentifier
        digestAlgorithmOID = new AlgorithmIdentifier(new DERObjectIdentifier(digestAlgorithmId.getOID().toString()), new DERNull());

        // digEncryptionAlgorithm
        final AlgorithmId keyAlgorithmId = AlgorithmId.get(keyAlgorithm);
        final AlgorithmIdentifier keyAlgorithmIdentifier;
        try {
            keyAlgorithmIdentifier = makeAlgId(keyAlgorithmId.getOID().toString(), keyAlgorithmId.getEncodedParams());
        }
        catch (final Exception e) {
            throw new IOException("Error de codificacion: " + e);
        }

        // SignerInfo
        final ASN1EncodableVector signerInfo = new ASN1EncodableVector();
        signerInfo.add(new SignerInfo(signerIdentifier, digestAlgorithmOID, signedAttributes, keyAlgorithmIdentifier, encodedPKCS1Signature, null // unsignedAttr
        ));

        final ASN1EncodableVector digestAlgorithms = new ASN1EncodableVector();
        digestAlgorithms.add(digestAlgorithmOID);
        
        ASN1Set certificates = null;
        if (signerCertificateChain.length != 0) {
            final List<DEREncodable> ce = new ArrayList<DEREncodable>();
            for (final X509Certificate element : signerCertificateChain) {
                ce.add(X509CertificateStructure.getInstance(ASN1Object.fromByteArray(element.getEncoded())));
            }
            certificates = createBerSetFromList(ce);
        }


        // construimos el Signed Data y lo devolvemos
        return new ContentInfo(PKCSObjectIdentifiers.signedData, new SignedData(new DERSet(digestAlgorithms),
                                                                                contentInfo,
                                                                                certificates,
                                                                                null,
                                                                                new DERSet(signerInfo))).getDEREncoded();

    }

    /** Realiza la firma usando los atributos del firmante.
     * @param signatureAlgorithm
     *        Algoritmo para la firma
     * @param keyEntry
     *        Clave para firmar.
     * @return Firma de los atributos.
     * @throws es.map.es.map.afirma.exceptions.AOException */
    private ASN1OctetString firma(final String signatureAlgorithm, final PrivateKeyEntry keyEntry) throws AOException {

        Signature sig = null;
        try {
            sig = Signature.getInstance(signatureAlgorithm);
        }
        catch (final Exception e) {
            throw new AOException("Error obteniendo la clase de firma para el algoritmo " + signatureAlgorithm, e);
        }

        byte[] tmp = null;

        try {
            tmp = signedAttr2.getEncoded(ASN1Encodable.DER);
        }
        catch (final IOException ex) {
            Logger.getLogger(GenSignedData.class.getName()).log(Level.SEVERE, ex.toString(), ex);
            throw new AOException("Error al detectar la codificacion de los datos ASN.1", ex);
        }

        // Indicar clave privada para la firma
        try {
            sig.initSign(keyEntry.getPrivateKey());
        }
        catch (final Exception e) {
            throw new AOException("Error al inicializar la firma con la clave privada", e);
        }

        // Actualizamos la configuracion de firma
        try {
            sig.update(tmp);
        }
        catch (final SignatureException e) {
            throw new AOException("Error al configurar la informacion de firma", e);
        }

        // firmamos.
        byte[] realSig = null;
        try {
            realSig = sig.sign();
        }
        catch (final Exception e) {
            throw new AOException("Error durante el proceso de firma", e);
        }

        final ASN1OctetString encDigest = new DEROctetString(realSig);

        return encDigest;

    }

}
