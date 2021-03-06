package finance.modelling.data.transform.transformexchanges.service;

import finance.modelling.data.transform.transformexchanges.repository.ExchangeRepository;
import finance.modelling.data.transform.transformexchanges.repository.config.MongoDbConfig;
import finance.modelling.data.transform.transformexchanges.repository.mapper.ExchangeMapper;
import finance.modelling.data.transform.transformexchanges.service.config.TopicConfig;
import finance.modelling.fmcommons.data.logging.LogDb;
import finance.modelling.fmcommons.data.logging.kstream.LogMessageConsumed;
import finance.modelling.fmcommons.data.schema.eod.dto.EodExchangeDTO;
import finance.modelling.fmcommons.data.schema.model.Exchange;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Consumer;

@Service
public class ExchangeDataModelServiceImpl implements ExchangeDataModelService {

    private final TopicConfig topics;
    private final ExchangeRepository exchangeRepository;
    private final MongoDbConfig dbConfig;

    public ExchangeDataModelServiceImpl(TopicConfig topics, ExchangeRepository exchangeRepository, MongoDbConfig dbConfig) {
        this.topics = topics;
        this.exchangeRepository = exchangeRepository;
        this.dbConfig = dbConfig;
    }

    @Bean
    public Consumer<KStream<String, EodExchangeDTO>> generateExchangeDataModel() {

        return eodExchanges -> eodExchanges
                .transformValues(() -> new LogMessageConsumed<>(topics.getTraceIdHeaderName()))
                .mapValues(ExchangeMapper.INSTANCE::exchangeEodDTOToExchange)
                .foreach(this::saveToExchangeRepository);
    }

    protected void saveToExchangeRepository(String _key, Exchange exchange) {
        Mono
                .just(exchange)
                .flatMap(exchangeRepository::save)
                .subscribe(
                        exchangeMono -> LogDb.logInfoDataItemSaved(
                                Exchange.class, exchangeMono.getExchangeCode(), dbConfig.getDbUri()),
                        error -> LogDb.logErrorFailedDataItemSave(
                                Exchange.class, error, dbConfig.getDbUri(), List.of("Log and fail"))
                );

    }
}
