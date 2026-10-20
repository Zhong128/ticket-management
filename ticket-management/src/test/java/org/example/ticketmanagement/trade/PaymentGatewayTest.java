package org.example.ticketmanagement.trade;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import org.example.ticketmanagement.config.AlipayProperties;
import org.example.ticketmanagement.trade.payment.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentGatewayTest {

    @Test
    void channelFailureIsUnknownAndNeverMeansUnpaid() throws Exception {
        var client = mock(AlipayClient.class);
        var response = new AlipayTradeQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.SYSTEM_ERROR");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        assertEquals(PaymentGateway.State.UNKNOWN,
                new AlipayPaymentGateway(client, new AlipayProperties()).query("P1").state());
    }

    @Test
    void onlyExplicitTradeNotExistMeansAbsent() throws Exception {
        var client = mock(AlipayClient.class);
        var response = new AlipayTradeQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.TRADE_NOT_EXIST");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        assertEquals(PaymentGateway.State.NOT_FOUND,
                new AlipayPaymentGateway(client, new AlipayProperties()).query("P1").state());
    }
}
