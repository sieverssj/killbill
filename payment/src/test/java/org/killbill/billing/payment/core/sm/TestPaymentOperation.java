/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.memory.MemoryGlobalLocker;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPaymentOperation extends PaymentTestSuiteNoDB {

    private PaymentStateContext paymentStateContext;
    private PaymentOperationTest paymentOperation;

    @Test(groups = "fast")
    public void testPaymentFailure() throws Exception {
        setUp(PaymentPluginStatus.ERROR);

        Assert.assertNull(paymentStateContext.getPaymentInfoPlugin());

        Assert.assertEquals(paymentOperation.doOperationCallback(), OperationResult.FAILURE);

        Assert.assertNotNull(paymentStateContext.getPaymentInfoPlugin());
    }

    @Test(groups = "fast")
    public void testPluginFailure() throws Exception {
        setUp(null);

        Assert.assertNull(paymentStateContext.getPaymentInfoPlugin());

        try {
            paymentOperation.doOperationCallback();
            Assert.fail();
        } catch (final OperationException e) {
            Assert.assertEquals(e.getOperationResult(), OperationResult.EXCEPTION);
        }

        Assert.assertNull(paymentStateContext.getPaymentInfoPlugin());
    }

    @Test(groups = "fast")
    public void testPaymentPending() throws Exception {
        setUp(PaymentPluginStatus.PENDING);

        Assert.assertNull(paymentStateContext.getPaymentInfoPlugin());

        Assert.assertEquals(paymentOperation.doOperationCallback(), OperationResult.PENDING);

        Assert.assertNotNull(paymentStateContext.getPaymentInfoPlugin());
    }

    @Test(groups = "fast")
    public void testPaymentSuccess() throws Exception {
        setUp(PaymentPluginStatus.PROCESSED);

        Assert.assertNull(paymentStateContext.getPaymentInfoPlugin());

        Assert.assertEquals(paymentOperation.doOperationCallback(), OperationResult.SUCCESS);

        Assert.assertNotNull(paymentStateContext.getPaymentInfoPlugin());
    }

    private void setUp(final PaymentPluginStatus paymentPluginStatus) throws Exception {
        final GlobalLocker locker = new MemoryGlobalLocker();
        final PluginDispatcher<OperationResult> paymentPluginDispatcher = new PluginDispatcher<OperationResult>(1, Executors.newCachedThreadPool());
        paymentStateContext = new PaymentStateContext(UUID.randomUUID(),
                                                                  null,
                                                                  UUID.randomUUID().toString(),
                                                                  UUID.randomUUID().toString(),
                                                                  TransactionType.CAPTURE,
                                                                  Mockito.mock(Account.class),
                                                                  UUID.randomUUID(),
                                                                  new BigDecimal("192.3920111"),
                                                                  Currency.BRL,
                                                                  false,
                                                                  ImmutableList.<PluginProperty>of(),
                                                                  internalCallContext,
                                                                  callContext);

        final PaymentMethodModelDao paymentMethodModelDao = new PaymentMethodModelDao(paymentStateContext.getPaymentMethodId(), UUID.randomUUID().toString(), clock.getUTCNow(), clock.getUTCNow(),
                                                                                      paymentStateContext.getAccount().getId(), MockPaymentProviderPlugin.PLUGIN_NAME, true);
        final PaymentDao paymentDao = Mockito.mock(PaymentDao.class);
        Mockito.when(paymentDao.getPaymentMethodIncludedDeleted(paymentStateContext.getPaymentMethodId(), internalCallContext)).thenReturn(paymentMethodModelDao);

        final PaymentAutomatonDAOHelper daoHelper = new PaymentAutomatonDAOHelper(paymentStateContext, clock.getUTCNow(), paymentDao, registry, internalCallContext, paymentSMHelper);
        paymentOperation = new PaymentOperationTest(paymentPluginStatus, daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
    }

    private static final class PaymentOperationTest extends PaymentOperation {

        private final PaymentTransactionInfoPlugin paymentInfoPlugin;

        public PaymentOperationTest(@Nullable final PaymentPluginStatus paymentPluginStatus,
                                          final PaymentAutomatonDAOHelper daoHelper, final GlobalLocker locker,
                                          final PluginDispatcher<OperationResult> paymentPluginDispatcher, final PaymentStateContext paymentStateContext) throws PaymentApiException {
            super(daoHelper, locker, paymentPluginDispatcher, paymentStateContext);
            this.paymentInfoPlugin = (paymentPluginStatus == null ? null : getPaymentInfoPlugin(paymentPluginStatus));
        }

        @Override
        protected PaymentTransactionInfoPlugin doCallSpecificOperationCallback() throws PaymentPluginApiException {
            if (paymentInfoPlugin == null) {
                throw new RuntimeException("Exception expected by test");
            } else {
                return paymentInfoPlugin;
            }
        }

        private PaymentTransactionInfoPlugin getPaymentInfoPlugin(final PaymentPluginStatus paymentPluginStatus) {
            final PaymentTransactionInfoPlugin paymentInfoPlugin = Mockito.mock(PaymentTransactionInfoPlugin.class);
            Mockito.when(paymentInfoPlugin.getStatus()).thenReturn(paymentPluginStatus);
            return paymentInfoPlugin;
        }
    }
}