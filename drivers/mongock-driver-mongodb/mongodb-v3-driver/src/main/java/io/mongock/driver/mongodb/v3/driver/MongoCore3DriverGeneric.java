package io.mongock.driver.mongodb.v3.driver;

import io.mongock.driver.api.driver.ChangeSetDependency;
import io.mongock.driver.api.driver.Transactioner;
import io.mongock.driver.api.entry.ChangeEntryService;
import io.mongock.driver.core.driver.ConnectionDriverBase;
import io.mongock.driver.core.lock.LockRepositoryWithEntity;
import io.mongock.driver.mongodb.v3.changelogs.runalways.MongockV3LegacyMigrationChangeRunAlwaysLog;
import io.mongock.driver.mongodb.v3.changelogs.runonce.MongockV3LegacyMigrationChangeLog;
import io.mongock.driver.mongodb.v3.repository.Mongo3ChangeEntryRepository;
import io.mongock.driver.mongodb.v3.repository.Mongo3LockRepository;
import io.mongock.driver.mongodb.v3.repository.ReadWriteConfiguration;
import io.mongock.api.exception.MongockException;
import io.mongock.utils.annotation.NotThreadSafe;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;

@NotThreadSafe
public abstract class MongoCore3DriverGeneric extends ConnectionDriverBase implements Transactioner {

  private static final WriteConcern DEFAULT_WRITE_CONCERN = WriteConcern.MAJORITY.withJournal(true);
  private static final ReadConcern DEFAULT_READ_CONCERN = ReadConcern.MAJORITY;
  private static final ReadPreference DEFAULT_READ_PREFERENCE = ReadPreference.primary();

  protected Mongo3ChangeEntryRepository changeEntryRepository;
  protected Mongo3LockRepository lockRepository;
  protected Set<ChangeSetDependency> dependencies;
  protected TransactionOptions txOptions;
  private WriteConcern writeConcern;
  private ReadConcern readConcern;
  private ReadPreference readPreference;
  protected final MongoDatabase mongoDatabase;

  protected MongoCore3DriverGeneric(MongoDatabase mongoDatabase,
                                 long lockAcquiredForMillis,
                                 long lockQuitTryingAfterMillis,
                                 long lockTryFrequencyMillis) {
    super(lockAcquiredForMillis, lockQuitTryingAfterMillis, lockTryFrequencyMillis);
    this.mongoDatabase = mongoDatabase;
  }

  /**
   * When using Java MongoDB driver directly, it sets the transaction options for all the Mongock's transactions.
   * Default: readPreference: primary, readConcern and writeConcern: majority
   * @param txOptions transaction options
   */
  public void setTransactionOptions(TransactionOptions txOptions) {
    this.txOptions = txOptions;
  }

  public void setWriteConcern(WriteConcern writeConcern) {
    this.writeConcern = writeConcern;
  }

  public void setReadConcern(ReadConcern readConcern) {
    this.readConcern = readConcern;
  }

  public void setReadPreference(ReadPreference readPreference) {
    this.readPreference = readPreference;
  }

  @Override
  public void runValidation() throws MongockException {
    if (mongoDatabase == null) {
      throw new MongockException("MongoDatabase cannot be null");
    }
    if (this.getLockManager() == null) {
      throw new MongockException("Internal error: Driver needs to be initialized by the runner");
    }
  }

  @Override
  protected LockRepositoryWithEntity getLockRepository() {
    if (lockRepository == null) {
      MongoCollection<Document> collection = mongoDatabase.getCollection(getLockRepositoryName());
      lockRepository = new Mongo3LockRepository(collection, getReadWriteConfiguration());
      lockRepository.setIndexCreation(isIndexCreation());
    }
    return lockRepository;
  }

  @Override
  public ChangeEntryService getChangeEntryService() {
    if (changeEntryRepository == null) {
      changeEntryRepository = new Mongo3ChangeEntryRepository(mongoDatabase.getCollection(getMigrationRepositoryName()), getReadWriteConfiguration());
      changeEntryRepository.setIndexCreation(isIndexCreation());
    }
    return changeEntryRepository;
  }

  @Override
  public Class getLegacyMigrationChangeLogClass(boolean runAlways) {
    return runAlways ? MongockV3LegacyMigrationChangeRunAlwaysLog.class : MongockV3LegacyMigrationChangeLog.class;
  }

  @Override
  public Set<ChangeSetDependency> getDependencies() {
    if (dependencies == null) {
      throw new MongockException("Driver not initialized");
    }
    return dependencies;
  }

  @Override
  public void specificInitialization() {
    dependencies = new HashSet<>();
    dependencies.add(new ChangeSetDependency(MongoDatabase.class, mongoDatabase, true));
    dependencies.add(new ChangeSetDependency(ChangeEntryService.class, getChangeEntryService(), false));
    this.txOptions = txOptions != null ? txOptions : buildDefaultTxOptions();
  }

  private TransactionOptions buildDefaultTxOptions() {
    return TransactionOptions.builder()
        .readPreference(ReadPreference.primary())
        .readConcern(ReadConcern.MAJORITY)
        .writeConcern(WriteConcern.MAJORITY)
        .build();
  }

  protected ReadWriteConfiguration getReadWriteConfiguration() {
    return new ReadWriteConfiguration(
        writeConcern != null ? writeConcern : DEFAULT_WRITE_CONCERN,
        readConcern != null ? readConcern : DEFAULT_READ_CONCERN,
        readPreference != null ? readPreference : DEFAULT_READ_PREFERENCE
    );
  }


}
