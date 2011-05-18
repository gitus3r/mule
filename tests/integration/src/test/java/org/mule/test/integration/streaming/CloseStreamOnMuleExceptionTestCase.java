/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.integration.streaming;

import org.mule.api.MuleException;
import org.mule.module.client.MuleClient;
import org.mule.module.xml.stax.DelegateXMLStreamReader;
import org.mule.module.xml.stax.StaxSource;
import org.mule.module.xml.util.XMLUtils;
import org.mule.tck.FunctionalTestCase;
import org.mule.util.concurrent.Latch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.InputSource;

public class CloseStreamOnMuleExceptionTestCase extends FunctionalTestCase
{
    private static Latch inputStreamLatch = new Latch();
    private static Latch streamReaderLatch = new Latch();
    private String xmlText = "<test attribute=\"1\"/>";
    private TestByteArrayInputStream inputStream;
    MuleClient client;

    @Override
    protected void doSetUp() throws Exception
    {
        super.doSetUp();
        client = new MuleClient(muleContext);
        inputStream = new TestByteArrayInputStream(xmlText.getBytes());
    }

    public void testCloseStreamOnComponentException() throws MuleException, InterruptedException
    {
        client.dispatch("vm://inEcho?connector=vm", inputStream, null);
        streamReaderLatch.await(1L, TimeUnit.SECONDS);
        assertTrue(inputStream.isClosed());
    }

    public void testCloseXMLInputSourceOnComponentException()
        throws MuleException, InterruptedException
    {
        InputSource stream = new InputSource(inputStream);

        client.dispatch("vm://inEcho?connector=vm", stream, null);

        streamReaderLatch.await(1L, TimeUnit.SECONDS);
        assertTrue(((TestByteArrayInputStream) stream.getByteStream()).isClosed());
    }

    public void testCloseXMLStreamSourceOnComponentException() throws FactoryConfigurationError, Exception
    {
        Source stream = XMLUtils.toXmlSource(XMLInputFactory.newInstance(), false, inputStream);

        client.dispatch("vm://inEcho?connector=vm", stream, null);

        streamReaderLatch.await(1L, TimeUnit.SECONDS);
        assertTrue(((TestByteArrayInputStream) ((StreamSource) stream).getInputStream()).isClosed());
    }

    public void testCloseXMLStreamReaderOnComponentException()
        throws MuleException, InterruptedException, XMLStreamException,
        FactoryConfigurationError
    {
        TestXMLStreamReader stream = new TestXMLStreamReader(XMLInputFactory.newInstance()
            .createXMLStreamReader(inputStream));

        client.dispatch("vm://inEcho?connector=vm", stream, null);

        streamReaderLatch.await(1L, TimeUnit.SECONDS);
        assertTrue(stream.isClosed());
    }

    public void testCloseSaxSourceOnComponentException()
        throws MuleException, InterruptedException, FactoryConfigurationError
    {
        SAXSource stream = new SAXSource(new InputSource(inputStream));

        client.dispatch("vm://inEcho?connector=vm", stream, null);

        Thread.sleep(1000);
        assertTrue(((TestByteArrayInputStream) stream.getInputSource().getByteStream()).isClosed());
    }

    public void testCloseStaxSourceOnComponentException()
        throws MuleException, InterruptedException, XMLStreamException,
        FactoryConfigurationError
    {

        StaxSource stream = new StaxSource(new TestXMLStreamReader(XMLInputFactory.newInstance()
            .createXMLStreamReader(inputStream)));

        client.dispatch("vm://inEcho?connector=vm", stream, null);

        Thread.sleep(1000);
        assertTrue(((TestXMLStreamReader) stream.getXMLStreamReader()).isClosed());
    }

    public void testCloseStreamOnDispatcherException()
        throws MuleException, InterruptedException
    {
        client.dispatch("vm://dispatcherExceptionBridge?connector=vm", inputStream, null);
        Thread.sleep(1000);
        assertTrue(inputStream.isClosed());
    }

    // TODO MULE-3558 Streams are not closed if there are exceptions in the message
    // receiver. Protocol/Transport workers should clean up after themselves if there
    // is an error (MULE-3559) but exceptions thrown by AbstractMessageReciever will
    // not result in stream being closed. These exceptions result in
    // exceptionStrategy being called but because RequestContext is empty the message
    // is not available in the AbstractExceptionListener and cannot be closed.

//    public void testCloseStreamOnInboundFilterException()
//        throws MuleException, InterruptedException
//    {
//        client.dispatch("vm://inboundFilterExceptionBridge?connector=vm", inputStream, null);
//
//        Thread.sleep(1000);
//
//        assertTrue(((TestByteArrayInputStream) inputStream).isClosed());
//    }


    @Override
    protected String getConfigResources()
    {
        return "org/mule/test/integration/streaming/close-stream-on-mule-exception-test.xml";
    }

    static class TestByteArrayInputStream extends ByteArrayInputStream
    {
        private boolean closed;

        public boolean isClosed()
        {
            return closed;
        }

        public TestByteArrayInputStream(byte[] arg0)
        {
            super(arg0);
        }

        public TestByteArrayInputStream(byte[] buf, int offset, int length)
        {
            super(buf, offset, length);
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            closed = true;
            inputStreamLatch.countDown();
        }
    }

    static class TestXMLStreamReader extends DelegateXMLStreamReader
    {
        private boolean closed;

        public boolean isClosed()
        {
            return closed;
        }

        public TestXMLStreamReader(XMLStreamReader reader)
        {
            super(reader);
        }

        @Override
        public void close() throws XMLStreamException
        {
            super.close();
            closed = true;
            streamReaderLatch.countDown();
        }
    }

}