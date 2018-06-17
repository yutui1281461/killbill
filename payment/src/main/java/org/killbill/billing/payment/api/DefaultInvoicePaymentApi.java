/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.payment.api.svcs.InvoicePaymentPaymentOptions;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.MoreObjects;
import com.google.inject.Inject;

public class DefaultInvoicePaymentApi implements InvoicePaymentApi {

    private final PaymentApi paymentApi;
    private final InvoiceInternalApi invoiceInternalApi;
    private final InvoicePaymentInternalApi invoicePaymentInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultInvoicePaymentApi(final PaymentApi paymentApi,
                                    final InvoiceInternalApi invoiceInternalApi,
                                    final InvoicePaymentInternalApi invoicePaymentInternalApi,
                                    final InternalCallContextFactory internalCallContextFactory) {
        this.paymentApi = paymentApi;
        this.invoiceInternalApi = invoiceInternalApi;
        this.invoicePaymentInternalApi = invoicePaymentInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public InvoicePayment createPurchaseForInvoice(final Account account,
                                                   final UUID invoiceId,
                                                   final UUID paymentMethodId,
                                                   final UUID paymentId,
                                                   final BigDecimal amount,
                                                   final Currency currency,
                                                   final DateTime effectiveDate,
                                                   final String paymentExternalKey,
                                                   final String paymentTransactionExternalKey,
                                                   final Iterable<PluginProperty> properties,
                                                   final PaymentOptions paymentOptions,
                                                   final CallContext context) throws PaymentApiException {
        return invoicePaymentInternalApi.createPurchaseForInvoice(true,
                                                                  account,
                                                                  invoiceId,
                                                                  paymentMethodId,
                                                                  paymentId,
                                                                  amount,
                                                                  currency,
                                                                  effectiveDate,
                                                                  paymentExternalKey,
                                                                  paymentTransactionExternalKey,
                                                                  properties,
                                                                  paymentOptions,
                                                                  internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public InvoicePayment createRefundForInvoice(final boolean isAdjusted,
                                                 final Map<UUID, BigDecimal> adjustments,
                                                 final Account account,
                                                 final UUID paymentId,
                                                 final BigDecimal amount,
                                                 final Currency currency,
                                                 final DateTime effectiveDate,
                                                 final String originalPaymentTransactionExternalKey,
                                                 final Iterable<PluginProperty> originalProperties,
                                                 final PaymentOptions paymentOptions,
                                                 final CallContext context) throws PaymentApiException {
        final Collection<PluginProperty> pluginProperties = preparePluginPropertiesForRefundOrCredit(isAdjusted, adjustments, originalProperties);
        final String paymentTransactionExternalKey = MoreObjects.firstNonNull(originalPaymentTransactionExternalKey, UUIDs.randomUUID().toString());

        final Payment payment = paymentApi.createRefundWithPaymentControl(account,
                                                                          paymentId,
                                                                          amount,
                                                                          currency,
                                                                          effectiveDate,
                                                                          paymentTransactionExternalKey,
                                                                          pluginProperties,
                                                                          InvoicePaymentPaymentOptions.create(paymentOptions),
                                                                          context);

        return getInvoicePayment(payment.getId(), paymentTransactionExternalKey, context);
    }

    @Override
    public InvoicePayment createCreditForInvoice(final boolean isAdjusted,
                                                 final Map<UUID, BigDecimal> adjustments,
                                                 final Account account,
                                                 final UUID originalPaymentId,
                                                 final UUID paymentMethodId,
                                                 final UUID paymentId,
                                                 final BigDecimal amount,
                                                 final Currency currency,
                                                 final DateTime effectiveDate,
                                                 final String paymentExternalKey,
                                                 final String originalPaymentTransactionExternalKey,
                                                 final Iterable<PluginProperty> originalProperties,
                                                 final PaymentOptions paymentOptions,
                                                 final CallContext context) throws PaymentApiException {
        final Collection<PluginProperty> pluginProperties = preparePluginPropertiesForRefundOrCredit(isAdjusted, adjustments, originalProperties);
        pluginProperties.add(new PluginProperty("IPCD_PAYMENT_ID", originalPaymentId, false));

        final String paymentTransactionExternalKey = MoreObjects.firstNonNull(originalPaymentTransactionExternalKey, UUIDs.randomUUID().toString());

        final Payment payment = paymentApi.createCreditWithPaymentControl(account,
                                                                          paymentId,
                                                                          null,
                                                                          amount,
                                                                          currency,
                                                                          effectiveDate,
                                                                          paymentExternalKey,
                                                                          paymentTransactionExternalKey,
                                                                          pluginProperties,
                                                                          InvoicePaymentPaymentOptions.create(paymentOptions),
                                                                          context);

        return getInvoicePayment(payment.getId(), paymentTransactionExternalKey, context);
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final TenantContext context) {
        return invoiceInternalApi.getInvoicePayments(paymentId, context);
    }

    @Override
    public List<InvoicePayment> getInvoicePaymentsByAccount(final UUID accountId, final TenantContext context) {
        return invoiceInternalApi.getInvoicePaymentsByAccount(accountId, context);
    }

    private Collection<PluginProperty> preparePluginPropertiesForRefundOrCredit(final boolean isAdjusted,
                                                                                final Map<UUID, BigDecimal> adjustments,
                                                                                final Iterable<PluginProperty> originalProperties) {
        final Collection<PluginProperty> pluginProperties = new LinkedList<PluginProperty>();
        if (originalProperties != null) {
            for (final PluginProperty pluginProperty : originalProperties) {
                pluginProperties.add(pluginProperty);
            }
        }
        pluginProperties.add(new PluginProperty("IPCD_REFUND_WITH_ADJUSTMENTS", isAdjusted, false));
        pluginProperties.add(new PluginProperty("IPCD_REFUND_IDS_AMOUNTS", adjustments, false));
        return pluginProperties;
    }

    private InvoicePayment getInvoicePayment(final UUID paymentId, final String paymentTransactionExternalKey, final TenantContext context) {
        for (final InvoicePayment invoicePayment : getInvoicePayments(paymentId, context)) {
            if (invoicePayment.getPaymentCookieId().compareTo(paymentTransactionExternalKey) == 0) {
                return invoicePayment;
            }
        }
        return null;
    }
}
