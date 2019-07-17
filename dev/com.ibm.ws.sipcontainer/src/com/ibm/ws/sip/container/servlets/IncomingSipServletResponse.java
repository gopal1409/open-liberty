/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.sip.container.servlets;

import jain.protocol.ip.sip.SipParseException;
import jain.protocol.ip.sip.SipProvider;
import jain.protocol.ip.sip.header.CSeqHeader;
import jain.protocol.ip.sip.header.ContactHeader;
import jain.protocol.ip.sip.header.HeaderParseException;
import jain.protocol.ip.sip.message.Request;
import jain.protocol.ip.sip.message.Response;

import java.io.IOException;

import javax.servlet.sip.Address;
import javax.servlet.sip.Rel100Exception;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.TooManyHopsException;
import javax.servlet.sip.UAMode;

import com.ibm.sip.util.log.Log;
import com.ibm.sip.util.log.LogMgr;
import com.ibm.ws.jain.protocol.ip.sip.extensions.RAckHeaderImpl;
import com.ibm.ws.jain.protocol.ip.sip.extensions.RSeqHeader;
import com.ibm.ws.jain.protocol.ip.sip.message.RequestImpl;
import com.ibm.ws.sip.container.tu.TransactionUserWrapper;

/**
 * @author Amir Perlman, Feb 24, 2003
 * Out Going Servlet Response. The respnose was generated by a remote party and
 * sent to the local party.  
 */
public class IncomingSipServletResponse extends SipServletResponseImpl
{
    /** Serialization UID (do not change) */
    static final long serialVersionUID = 4841030986553876807L;

	/**
	 * Class Logger. 
	 */
	private static final LogMgr c_logger =
		Log.get(IncomingSipServletResponse.class);
    
	/**
	 * Indicates whether an ACK has already been created for this response.
	 */
	private boolean m_ackCreated;
	
    /**
     * public no-arg constructor to satisfy Externalizable.readExternal()
     */
    public IncomingSipServletResponse() {
    }
      
    /**
     * Constructor for Incoming Servlet Response.
     * @param response
     * @param transactionId
     * @param provider
     */
    public IncomingSipServletResponse(Response response,
        							long transactionId,
        							SipProvider provider)
    {
        super(response, transactionId, provider);
        
        if (isReliableResponse()){
        	if (c_logger.isTraceDebugEnabled()) {
				c_logger.traceDebug(this, "IncomingSipServletResponse", 
					"the message is an incoming reliable provisional response, " +
					"and there for un-committed");
			}
        	setIsCommited(false);
        }
        
        else if(getStatus()>=200 && getStatus()<300 && 
        		response.getCSeqHeader().getMethod().equals(Request.INVITE)){
        	
        	if (c_logger.isTraceDebugEnabled()) {
				c_logger.traceDebug(this, "IncomingSipServletResponse", 
				"message is an incoming final response to an INVITE transaction - set un-committed stated");
			}
        	setIsCommited(false);
        }
    }
	
    /**
	 * Overrides the method in SipServletMessage to add the ability to get 
	 * Session by the To tag that is usefull in the Derived Session state 
	 * @see javax.servlet.sip.SipServletMessage#getSession(boolean)
	 * @param create
	 * @return
	 */
    @Override
	public SipSession getProxySession(boolean create) {
		return getTransactionUser().getSipSession(create);
	}
    
    
    /**
     * @see com.ibm.ws.sip.container.servlets.SipServletMessageImpl#getLocalParty()
     */
    @Override
	Address getLocalParty()
    {
        return getTo();
    }

    /**
     * @see com.ibm.ws.sip.container.servlets.SipServletMessageImpl#getRemoteParty()
     */
    @Override
	Address getRemoteParty()
    {
        return getFrom();
    }
    
	/**
	 * @see javax.servlet.sip.SipServletMessage#send()
	 */
	public void send() throws IOException
	{
		// throw IllegalStateException, because the transaction for an incoming
		// response is a client transaction, and client transactions do not
		// send responses.
		String err = "Can not send a Committed Response";
		
		//Do nothing this Response was already sent.
		if (c_logger.isTraceDebugEnabled())
		{
			c_logger.traceDebug(this, "send", err);
		} 
		 
		throw new IllegalStateException(err);   
	}
    
    /**
     * @see javax.servlet.sip.SipServletResponse#sendReliably()
     */
    @Override
	public void sendReliably() throws Rel100Exception
    {
    	if (!isLiveMessage("sendReliably"))
    		return;

    	if (c_logger.isTraceDebugEnabled())
		{
			c_logger.traceDebug(this, "sendReliably", 
							"Can not send a Committed Response");
		}
		
    	throw new Rel100Exception(Rel100Exception.NOT_SUPPORTED);
    }
    
    /**
     *  @see javax.servlet.sip.SipServletResponse#createPrack()
     */
	public SipServletRequest createPrack() throws Rel100Exception {
    	setIsCommited(true);
    	
    	if(!isReliableResponse()){
    		throw new Rel100Exception(Rel100Exception.NOT_SUPPORTED);
    	}
    	
    	SipServletRequestImpl prackReq = (SipServletRequestImpl)getTransactionUser().createRequest(RequestImpl.PRACK);
    	try {
            RSeqHeader h = (RSeqHeader)getResponse().getHeader(RSeqHeader.name,true);
            long rSeq = 0;
            if(h!= null){
                rSeq = h.getResponseNumber();
            }
            CSeqHeader ch = (CSeqHeader)getResponse().getHeader(CSeqHeader.name,true);
            long cSeq = 0;
            if(h!= null){
                cSeq = ch.getSequenceNumber();
            }
            RAckHeaderImpl rack = new RAckHeaderImpl();
            rack.setResponseNumber(rSeq);
            rack.setSequenceNumber(cSeq);
            rack.setMethod(getRequest().getMethod());
            prackReq.getRequest().setHeader(rack,true);            
        } 
    	catch (HeaderParseException e) {
            // We should not get here - because we had created this RSeq in the SendReliable() method
            if (c_logger.isTraceDebugEnabled()) {
                c_logger.traceDebug(this, "ReliableResponse", "Somthing was wrong in the RSeqHeader");
            }
            throw new IllegalStateException("Corrupted RSeqHeader in incoming response");
        } 
        catch (IllegalArgumentException e) {
            // We should not get here - because we had created this RSeq in the SendReliable() method
            if (c_logger.isTraceDebugEnabled()) {
                c_logger.traceDebug(this, "ReliableResponse", "Corrupted RSeqHeader in incoming response");
            }
            throw new IllegalStateException("Corrupted RSeqHeader in incoming response");
            
        } 
        catch (SipParseException e) {
        	if (c_logger.isTraceDebugEnabled()) {
                c_logger.traceDebug(this, "ReliableResponse", "Failed to generate RSeq header");
            }
            throw new IllegalStateException("Failed to generate RSeq header");
		}
    	
    	return prackReq;
    }
    
    /**
     * @see javax.servlet.sip.SipServletResponse#createAck()
     */
    @Override
	public SipServletRequest createAck()
    {
    	if (!isLiveMessage("createAck"))
    		return null;
        
        if(!getMethod().equals(Request.INVITE)) {
            throw new IllegalStateException("ACK can only be created for INVITE requests");
        }
        
        if(m_ackCreated) {
            throw new IllegalStateException("ACK can only be generated once per response");
        }
        try {

			if(getRequest().getProxy(false) != null) {
			    throw new IllegalStateException("ACK Cannot be generated by the application in proxy mode");
			}
			
		} catch (TooManyHopsException e) {
			if (c_logger.isTraceDebugEnabled()) {
	            c_logger.traceDebug(this, "createAck", "Cannot get proxy object - Too many hops");
	        }
		}
        //message is an incoming final response to an INVITE transaction
        //and an ACK has been generated 
        setIsCommited(true);
        OutgoingSipServletAckRequest ack = new OutgoingSipServletAckRequest(this);
        m_ackCreated = true;
        return ack;
    }
    
        
	/**
     * @see javax.servlet.sip.SipServletMessage#getLocalAddr()
     */
	@Override
	public String getLocalAddr()
	{
		// @return null if it was locally generated.
		if (_isInternallyGenerated) {
			return null;
		}
//      Assumption is that nobody can change the "IBM-Client-Address" header
    	if (null == m_localAddr)
    	{
	        try {
	        	parseTransport();
			} catch (HeaderParseException e) {
				log("getLocaleAddr", "Unable to get local transport", e);
			}
	    }
        return m_localAddr;
	}

	/**
	 * @see javax.servlet.sip.SipServletMessage#getLocalPort()
	 */
	@Override
	public int getLocalPort()
	{
		// @return -1 if it was locally generated.
		if (_isInternallyGenerated) {
			return -1;
		}
//      Assumption is that nobody can change the "IBM-Client-Address" header
    	if (-1 == m_localPort)
    	{
	        try {
	        	parseTransport();
			} catch (HeaderParseException e) {
				log("getLocalPort", "Unable to get local transport", e);
			}
	    }
        return m_localPort;
    }

	/**
	 * @see javax.servlet.sip.SipServletMessage#getTransport()
	 */
	@Override
	public String getTransport()
	{
    	if (!isLiveMessage("getTransport"))
    		return null;
    	
		return  getSipProvider().getListeningPoint().getTransport();
	}

	/**
     * @see javax.servlet.sip.SipServletMessage#getRemoteAddr()
     */
    @Override
	public String getRemoteAddr()
    {
    	// @return null if it was locally generated.
		if (_isInternallyGenerated) {
			return null;
		}
//      Assumption is that nobody can change the "IBM-Client-Address" header
    	if (null == m_remoteAddr)
    	{
	        try {
	        	parseTransport();
			} catch (HeaderParseException e) {
				log("getRemoteAddr", "Unable to get remote transport", e);
			}
	    }
        return m_remoteAddr;
    }

    /**
     * @see javax.servlet.sip.SipServletMessage#getRemotePort()
     */
    @Override
	public int getRemotePort()
    {
    	// @return -1 if it was locally generated.
		if (_isInternallyGenerated) {
			return -1;
		}
//      Assumption is that nobody can change the "IBM-Client-Address" header
    	if (-1 == m_remotePort)
    	{
	        try {
	        	parseTransport();
			} catch (HeaderParseException e) {
				log("getRemotePort", "Unable to get remote transport", e);
			}
	    }
        return m_remotePort;
    }
    
    /**
     * 
     * @param isCommited
     * @return
     */
    @Override
	protected void updateUnCommittedMessagesList(boolean isCommited){
    	TransactionUserWrapper transactionUser = getTransactionUser();
    	if(isCommited){
    		transactionUser.removeB2BPendingMsg(this, UAMode.UAC);
    	} else {
    		transactionUser.addB2BPendingMsg(this, UAMode.UAC);
    	}
    }
    
    /**
     * @see com.ibm.ws.sip.container.servlets.SipServletMessageImpl#createContactHeader()
     */
    protected ContactHeader createContactHeader() throws SipParseException {
    	// This is not relevant to the message context
    	return null;
    }
    
    @Override
    protected boolean shouldCreateContactIfNotExist() {
    	if (c_logger.isTraceDebugEnabled())
        {
            c_logger.traceDebug(this, "shouldCreateContactIfNotExist", "shouldn't create contact on incoming response");
        }
    	return false;
    }
    
    /**
     * Sets if the response generated internally
     * @param isInternal
     */
    public void setInternal(boolean isInternal) {
    	_isInternallyGenerated = isInternal;
	}
}