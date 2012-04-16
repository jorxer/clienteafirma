package net.java.xades.security.xml.XAdES;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import net.java.xades.security.timestamp.TimeStampFactory;

import org.w3c.dom.Document;

public class IndividualDataObjectsTimeStampImpl implements IndividualDataObjectsTimeStamp
{
    private byte[] data;
    
    public IndividualDataObjectsTimeStampImpl(byte[] data)
    {
        this.data = data;
    }
    
    public byte[] generateEncapsulatedTimeStamp(Document parent, String tsaURL) throws NoSuchAlgorithmException, SignatureException, IOException
    {        
        return TimeStampFactory.getTimeStamp(tsaURL, this.data, true);
    }
}
