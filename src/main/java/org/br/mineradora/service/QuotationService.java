package org.br.mineradora.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.br.mineradora.client.CurrencyPriceClient;
import org.br.mineradora.dto.CurrencyPriceDTO;
import org.br.mineradora.dto.QuotationDTO;
import org.br.mineradora.entity.QuotationEntity;
import org.br.mineradora.message.KafkaEvents;
import org.br.mineradora.repository.QuotationRepository;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@ApplicationScoped
public class QuotationService {

    private final Logger LOG = LoggerFactory.getLogger(QuotationService.class);

    @Inject
    @RestClient
    CurrencyPriceClient currencyPriceClient;

    @Inject
    QuotationRepository quotationRepository;

    @Inject
    KafkaEvents kafkaEvents;

    public void getCurrencyPrice() {
        var currencyPriceInfo = currencyPriceClient.getPriceByPair("USD-BRL");

        if (currencyPriceInfo != null && currencyPriceInfo.getUSDBRL() != null) {
            if (updateCurrentInfoPrice(currencyPriceInfo)) {
                kafkaEvents.sendNewKafkaEvent(
                        QuotationDTO
                                .builder()
                                .currencyPrice(new BigDecimal(currencyPriceInfo.getUSDBRL().getBid()))
                                .date(new Date())
                                .build()
                );
            }
        } else {
            LOG.error("CurrencyPriceDTO ou USDBRL esta null.");
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
            var lastDollarPrice = quotationEntityList.get(quotationEntityList.size() - 1);

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
