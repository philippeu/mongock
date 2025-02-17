package io.mongock.driver.mongodb.springdata.v3;

import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate;
import io.mongock.api.exception.MongockException;
import io.mongock.driver.api.driver.ChangeSetDependency;
import io.mongock.driver.api.driver.ChangeSetDependencyBuildable;
import io.mongock.driver.api.driver.Transactioner;
import io.mongock.driver.api.entry.ChangeEntryService;
import io.mongock.driver.mongodb.sync.v4.driver.MongoSync4DriverGeneric;
import io.mongock.utils.annotation.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.SessionSynchronization;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Optional;

@NotThreadSafe
public abstract class SpringDataMongoV3DriverBase extends MongoSync4DriverGeneric {

  protected static final Logger logger = LoggerFactory.getLogger(SpringDataMongoV3DriverBase.class);

  protected final MongoTemplate mongoTemplate;
  protected PlatformTransactionManager txManager;

  protected SpringDataMongoV3DriverBase(MongoTemplate mongoTemplate,
                                    long lockAcquiredForMillis,
                                    long lockQuitTryingAfterMillis,
                                    long lockTryFrequencyMillis) {
    super(mongoTemplate.getDb(), lockAcquiredForMillis, lockQuitTryingAfterMillis, lockTryFrequencyMillis);
    this.mongoTemplate = mongoTemplate;
  }


  @Override
  public void runValidation() throws MongockException {
    super.runValidation();
    if (this.mongoTemplate == null) {
      throw new MongockException("MongoTemplate must not be null");
    }
  }



  @Override
  public void specificInitialization() {
    super.specificInitialization();
    dependencies.add(new ChangeSetDependencyBuildable(
        MongockTemplate.class,
        MongoTemplate.class,
        impl -> new MongockTemplate((MongoTemplate) impl),
        true));
    dependencies.add(new ChangeSetDependency(MongoTemplate.class, this.mongoTemplate));
  }

  public MongockTemplate getMongockTemplate() {
    if (!isInitialized()) {
      throw new MongockException("Mongock Driver hasn't been initialized yet");
    }
    return dependencies
        .stream()
        .filter(dependency -> MongockTemplate.class.isAssignableFrom(dependency.getType()))
        .map(ChangeSetDependency::getInstance)
        .map(instance -> (MongockTemplate) instance)
        .findAny()
        .orElseThrow(() -> new MongockException("Mongock Driver hasn't been initialized yet"));

  }

  @Override
  public ChangeEntryService getChangeEntryService() {
    if (changeEntryRepository == null) {
      changeEntryRepository = new SpringDataMongoV3ChangeEntryRepository(mongoTemplate, getMigrationRepositoryName(), getReadWriteConfiguration());
      changeEntryRepository.setIndexCreation(isIndexCreation());
    }
    return changeEntryRepository;
  }

  public void disableTransaction() {
    this.txManager = null;
  }

  public void enableTransactionWithTxManager(PlatformTransactionManager txManager) {
    this.txManager = txManager;
  }

  @Override
  public Optional<Transactioner> getTransactioner() {
    return Optional.ofNullable(txManager != null ? this : null);
  }

  @Override
  public void executeInTransaction(Runnable operation) {
    TransactionStatus txStatus = getTxStatus(txManager);
    try {
      mongoTemplate.setSessionSynchronization(SessionSynchronization.ALWAYS);
      operation.run();
      txManager.commit(txStatus);
    } catch (Exception ex) {
      logger.warn("Error in Mongock's transaction", ex);
      txManager.rollback(txStatus);
      throw new MongockException(ex);
    }

  }

  protected TransactionStatus getTxStatus(PlatformTransactionManager txManager) {
    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
// explicitly setting the transaction name is something that can be done only programmatically
    def.setName("mongock-transaction-spring-data-3");
    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    return txManager.getTransaction(def);
  }
}
