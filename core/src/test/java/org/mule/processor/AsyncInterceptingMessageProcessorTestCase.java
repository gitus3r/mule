/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.processor;

import org.mule.MessageExchangePattern;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.context.WorkManager;
import org.mule.api.context.WorkManagerSource;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.transaction.Transaction;
import org.mule.tck.AbstractMuleTestCase;
import org.mule.tck.testmodels.mule.TestTransaction;
import org.mule.transaction.TransactionCoordination;
import org.mule.util.concurrent.Latch;

import java.beans.ExceptionListener;
import java.util.concurrent.TimeUnit;

public class AsyncInterceptingMessageProcessorTestCase extends AbstractMuleTestCase
    implements ExceptionListener
{

    protected MessageProcessor messageProcessor;
    protected TestListener target = new TestListener();
    protected Exception exceptionThrown;
    protected Latch latch = new Latch();

    public AsyncInterceptingMessageProcessorTestCase()
    {
        setStartContext(true);
    }

    @Override
    protected void doSetUp() throws Exception
    {
        super.doSetUp();
        messageProcessor = createAsyncInterceptingMessageProcessor(target);
    }

    public void testProcessOneWay() throws Exception
    {
        MuleEvent event = getTestEvent(TEST_MESSAGE, getTestInboundEndpoint(MessageExchangePattern.ONE_WAY));

        MuleEvent result = messageProcessor.process(event);

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertNotNull(target.sensedEvent);
        // Event is not the same because it gets copied in
        // AbstractMuleEventWork#run()
        assertNotSame(event, target.sensedEvent);
        assertEquals(event.getMessageAsString(), target.sensedEvent.getMessageAsString());

        assertNull(result);
        assertNull(exceptionThrown);
    }

    public void testProcessRequestResponse() throws Exception
    {
        MuleEvent event = getTestEvent(TEST_MESSAGE, getTestInboundEndpoint(MessageExchangePattern.ONE_WAY));

        assertAsync(messageProcessor, event);
    }

    public void testProcessOneWayWithTx() throws Exception
    {
        MuleEvent event = getTestEvent(TEST_MESSAGE,
            getTestTransactedInboundEndpoint(MessageExchangePattern.ONE_WAY));
        Transaction transaction = new TestTransaction(muleContext);
        TransactionCoordination.getInstance().bindTransaction(transaction);

        try
        {
            messageProcessor.process(event);
            fail("Exception expected");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof MessagingException);
            assertNull(target.sensedEvent);
        }
        finally
        {
            TransactionCoordination.getInstance().unbindTransaction(transaction);
        }
    }

    public void testProcessRequestResponseWithTx() throws Exception
    {
        MuleEvent event = getTestEvent(TEST_MESSAGE,
            getTestTransactedInboundEndpoint(MessageExchangePattern.REQUEST_RESPONSE));
        Transaction transaction = new TestTransaction(muleContext);
        TransactionCoordination.getInstance().bindTransaction(transaction);

        try
        {
            messageProcessor.process(event);
            fail("Exception expected");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof MessagingException);
            assertNull(target.sensedEvent);
        }
        finally
        {
            TransactionCoordination.getInstance().unbindTransaction(transaction);
        }
    }

    protected void assertSync(MessageProcessor processor, MuleEvent event) throws MuleException
    {
        MuleEvent result = processor.process(event);

        assertSame(event, target.sensedEvent);
        assertSame(event, result);
    }

    protected void assertAsync(MessageProcessor processor, MuleEvent event)
        throws MuleException, InterruptedException
    {
        MuleEvent result = processor.process(event);

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertNotNull(target.sensedEvent);
        // Event is not the same because it gets copied in
        // AbstractMuleEventWork#run()
        assertNotSame(event, target.sensedEvent);
        assertEquals(event.getMessageAsString(), target.sensedEvent.getMessageAsString());

        assertNull(result);
        assertNull(exceptionThrown);
    }

    protected AsyncInterceptingMessageProcessor createAsyncInterceptingMessageProcessor(MessageProcessor listener)
        throws Exception
    {
        AsyncInterceptingMessageProcessor mp = new AsyncInterceptingMessageProcessor(
            new TestWorkManagerSource(), true);
        mp.setListener(listener);
        return mp;
    }

    class TestListener implements MessageProcessor
    {
        MuleEvent sensedEvent;

        public MuleEvent process(MuleEvent event) throws MuleException
        {
            sensedEvent = event;
            latch.countDown();
            return event;
        }
    }

    public void exceptionThrown(Exception e)
    {
        exceptionThrown = e;
    }

    class TestWorkManagerSource implements WorkManagerSource
    {
        public WorkManager getWorkManager() throws MuleException
        {
            return muleContext.getWorkManager();
        }
    }

}