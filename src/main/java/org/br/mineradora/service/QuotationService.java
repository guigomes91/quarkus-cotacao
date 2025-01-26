package org.br.mineradora.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.br.mineradora.client.CurrencyPriceClient;
import org.br.mineradora.dto.CurrencyPriceDTO;
import org.br.mineradora.dto.QuotationDTO;
import org.br.mineradora.dto.USDBRL;
import org.br.mineradora.entity.QuotationEntity;
import org.br.mineradora.message.KafkaEvents;
import org.br.mineradora.repository.QuotationRepository;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class QuotationService {

    @Inject
    @RestClient
    CurrencyPriceClient currencyPriceClient;

    @Inject
    QuotationRepository quotationRepository;

    @Inject
    KafkaEvents kafkaEvents;

    public void getCurrencyPrice() {
        var currencyPriceInfo = currencyPriceClient.getPriceByPair("USD-BRL");

        if (updateCurrentInfoPrice(currencyPriceInfo)) {
            kafkaEvents.sendNewKafkaEvent(
                    QuotationDTO
                            .builder()
                            .currencyPrice(new BigDecimal(currencyPriceInfo.getUSDBRL().getBid()))
                            .date(new Date())
                            .build()
            );
        }
    }

    private boolean updateCurrentInfoPrice(CurrencyPriceDTO currencyPriceDTO) {
        var currentPrice = new BigDecimal(currencyPriceDTO.getUSDBRL().getBid());
        var updatePrice = false;

        List<QuotationEntity> quotationEntityList = quotationRepository.findAll().list();

        if (quotationEntityList.isEmpty()) {
            saveQuotation(currencyPriceDTO);
            updatePrice = true;
        } else {
            var lastDollarPrice = quotationEntityList.getLast();

            if (currentPrice.floatValue() > lastDollarPrice.getCurrencyPrice().floatValue()) {
                updatePrice = true;
                saveQuotation(currencyPriceDTO);
            }
        }

        return updatePrice;
    }

    private void saveQuotation(CurrencyPriceDTO currencyPriceDTO) {
        var quotation = new QuotationEntity();

        quotation.setDate(new Date());
        quotation.setCurrencyPrice(new BigDecimal(currencyPriceDTO.getUSDBRL().getBid()));
        quotation.setPctChange(currencyPriceDTO.getUSDBRL().getPctChange());
        quotation.setPair("USD-BRL");

        quotationRepository.persist(quotation);
    }
}
