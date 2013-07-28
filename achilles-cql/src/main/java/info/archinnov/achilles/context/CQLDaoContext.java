package info.archinnov.achilles.context;

import static info.archinnov.achilles.counter.AchillesCounter.CQLQueryType.*;
import info.archinnov.achilles.counter.AchillesCounter.CQLQueryType;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.exception.AchillesException;
import info.archinnov.achilles.statement.cache.CacheManager;
import info.archinnov.achilles.statement.cache.StatementCacheKey;
import info.archinnov.achilles.statement.prepared.CQLPreparedStatementBinder;
import info.archinnov.achilles.type.ConsistencyLevel;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Query;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.cache.Cache;

/**
 * CQLDaoContext
 * 
 * @author DuyHai DOAN
 * 
 */
public class CQLDaoContext
{
    private static final String ACHILLES_DML_STATEMENT = "ACHILLES_DML_STATEMENT";

    private static final Logger dmlLogger = LoggerFactory.getLogger(ACHILLES_DML_STATEMENT);

    private Map<Class<?>, PreparedStatement> insertPSs;
    private Cache<StatementCacheKey, PreparedStatement> dynamicPSCache;
    private Map<Class<?>, PreparedStatement> selectEagerPSs;
    private Map<Class<?>, Map<String, PreparedStatement>> removePSs;
    private Map<CQLQueryType, PreparedStatement> counterQueryMap;
    private Map<Class<?>, Map<CQLQueryType, PreparedStatement>> clusteredCounterQueryMap;
    private Session session;

    private CQLPreparedStatementBinder binder = new CQLPreparedStatementBinder();
    private CacheManager cacheManager = new CacheManager();

    public CQLDaoContext(Map<Class<?>, PreparedStatement> insertPSs,
            Cache<StatementCacheKey, PreparedStatement> dynamicPSCache,
            Map<Class<?>, PreparedStatement> selectEagerPSs,
            Map<Class<?>, Map<String, PreparedStatement>> removePSs,
            Map<CQLQueryType, PreparedStatement> counterQueryMap,
            Map<Class<?>, Map<CQLQueryType, PreparedStatement>> clusteredCounterQueryMap,
            Session session)
    {
        this.insertPSs = insertPSs;
        this.dynamicPSCache = dynamicPSCache;
        this.selectEagerPSs = selectEagerPSs;
        this.removePSs = removePSs;
        this.counterQueryMap = counterQueryMap;
        this.clusteredCounterQueryMap = clusteredCounterQueryMap;
        this.session = session;
    }

    public void bindForInsert(CQLPersistenceContext context)
    {
        EntityMeta entityMeta = context.getEntityMeta();
        Class<?> entityClass = context.getEntityClass();
        PreparedStatement ps = insertPSs.get(entityClass);
        BoundStatement bs = binder.bindForInsert(ps, entityMeta, context.getEntity());
        context.pushBoundStatement(bs, entityMeta.getWriteConsistencyLevel());
    }

    public void bindForUpdate(CQLPersistenceContext context, List<PropertyMeta<?, ?>> pms)
    {
        EntityMeta entityMeta = context.getEntityMeta();
        PreparedStatement ps = cacheManager.getCacheForFieldsUpdate(session, dynamicPSCache,
                context, pms);
        BoundStatement bs = binder.bindForUpdate(ps, entityMeta, pms, context.getEntity());
        context.pushBoundStatement(bs, entityMeta.getWriteConsistencyLevel());
    }

    public boolean checkForEntityExistence(CQLPersistenceContext context)
    {
        EntityMeta entityMeta = context.getEntityMeta();
        PreparedStatement ps = cacheManager.getCacheForFieldSelect(session, dynamicPSCache,
                context, entityMeta.getIdMeta());
        return executeReadWithConsistency(context, ps, entityMeta.getReadConsistencyLevel()).size() == 1;
    }

    public Row loadProperty(CQLPersistenceContext context, PropertyMeta<?, ?> pm)
    {
        PreparedStatement ps = cacheManager.getCacheForFieldSelect(session, dynamicPSCache,
                context, pm);
        List<Row> rows = executeReadWithConsistency(context, ps, pm.getReadConsistencyLevel());
        return returnFirstRowOrNull(rows);
    }

    public void bindForRemoval(CQLPersistenceContext context, String tableName,
            ConsistencyLevel writeLevel)
    {
        EntityMeta entityMeta = context.getEntityMeta();
        Class<?> entityClass = context.getEntityClass();
        Map<String, PreparedStatement> psMap = removePSs.get(entityClass);
        if (psMap.containsKey(tableName))
        {

            BoundStatement bs = binder.bindStatementWithOnlyPKInWhereClause(psMap.get(tableName),
                    entityMeta, context.getPrimaryKey());
            context.pushBoundStatement(bs, writeLevel);
        }
        else
        {
            throw new AchillesException("Cannot find prepared statement for deletion for table '"
                    + tableName + "'");
        }
    }

    // Simple counter
    public void bindForSimpleCounterIncrement(CQLPersistenceContext context, EntityMeta meta,
            PropertyMeta<?, ?> counterMeta, Long increment)
    {
        PreparedStatement ps = counterQueryMap.get(INCR);
        BoundStatement bs = binder.bindForSimpleCounterIncrementDecrement(ps, meta, counterMeta,
                context.getPrimaryKey(), increment);

        ConsistencyLevel consistency = context.getWriteConsistencyLevel().isPresent() ? context
                .getWriteConsistencyLevel().get() : counterMeta.getWriteConsistencyLevel();
        context.pushBoundStatement(bs, consistency);
    }

    public void incrementSimpleCounter(CQLPersistenceContext context, EntityMeta meta,
            PropertyMeta<?, ?> counterMeta, Long increment, ConsistencyLevel consistencyLevel)
    {
        PreparedStatement ps = counterQueryMap.get(INCR);
        BoundStatement bs = binder.bindForSimpleCounterIncrementDecrement(ps, meta, counterMeta,
                context.getPrimaryKey(), increment);
        context.executeImmediateWithConsistency(bs, consistencyLevel);
    }

    public void decrementSimpleCounter(CQLPersistenceContext context, EntityMeta meta,
            PropertyMeta<?, ?> counterMeta, Long decrement, ConsistencyLevel consistencyLevel)
    {
        PreparedStatement ps = counterQueryMap.get(DECR);
        BoundStatement bs = binder.bindForSimpleCounterIncrementDecrement(ps, meta, counterMeta,
                context.getPrimaryKey(), decrement);
        context.executeImmediateWithConsistency(bs, consistencyLevel);
    }

    public Row getSimpleCounter(CQLPersistenceContext context, PropertyMeta<?, ?> counterMeta,
            ConsistencyLevel consistencyLevel)
    {
        PreparedStatement ps = counterQueryMap.get(SELECT);
        BoundStatement bs = binder.bindForSimpleCounterSelect(ps, context.getEntityMeta(), counterMeta,
                context.getPrimaryKey());
        ConsistencyLevel readLevel = consistencyLevel != null ? consistencyLevel : counterMeta
                .getWriteConsistencyLevel();
        ResultSet resultSet = context.executeImmediateWithConsistency(bs, readLevel);

        return returnFirstRowOrNull(resultSet.all());
    }

    public void bindForSimpleCounterDelete(CQLPersistenceContext context, EntityMeta meta,
            PropertyMeta<?, ?> counterMeta, Object primaryKey)
    {
        PreparedStatement ps = counterQueryMap.get(DELETE);
        BoundStatement bs = binder.bindForSimpleCounterDelete(ps, meta, counterMeta, primaryKey);

        ConsistencyLevel consistency = context.getWriteConsistencyLevel().isPresent() ? context
                .getWriteConsistencyLevel().get() : counterMeta.getWriteConsistencyLevel();
        context.pushBoundStatement(bs, consistency);
    }

    // Clustered counter
    public void bindForClusteredCounterIncrement(CQLPersistenceContext context, EntityMeta meta,
            PropertyMeta<?, ?> counterMeta, Long increment)
    {
        PreparedStatement ps = clusteredCounterQueryMap.get(meta.getEntityClass()).get(INCR);
        BoundStatement bs = binder.bindForClusteredCounterIncrementDecrement(ps, meta, counterMeta,
                context.getPrimaryKey(), increment);
        ConsistencyLevel consistency = context.getWriteConsistencyLevel().isPresent() ? context
                .getWriteConsistencyLevel().get() : counterMeta.getWriteConsistencyLevel();
        context.pushBoundStatement(bs, consistency);
    }

    public void incrementClusteredCounter(CQLPersistenceContext context, EntityMeta meta,
            PropertyMeta<?, ?> counterMeta, Long increment, ConsistencyLevel consistencyLevel)
    {
        PreparedStatement ps = clusteredCounterQueryMap.get(meta.getEntityClass()).get(INCR);
        BoundStatement bs = binder.bindForClusteredCounterIncrementDecrement(ps, meta, counterMeta,
                context.getPrimaryKey(), increment);
        context.executeImmediateWithConsistency(bs, consistencyLevel);
    }

    public void decrementClusteredCounter(CQLPersistenceContext context, EntityMeta meta,
            PropertyMeta<?, ?> counterMeta, Long decrement, ConsistencyLevel consistencyLevel)
    {
        PreparedStatement ps = clusteredCounterQueryMap.get(meta.getEntityClass()).get(DECR);
        BoundStatement bs = binder.bindForClusteredCounterIncrementDecrement(ps, meta, counterMeta,
                context.getPrimaryKey(), decrement);
        context.executeImmediateWithConsistency(bs, consistencyLevel);
    }

    public Row getClusteredCounter(CQLPersistenceContext context, PropertyMeta<?, ?> counterMeta,
            ConsistencyLevel consistencyLevel)
    {
        EntityMeta entityMeta = context.getEntityMeta();
        PreparedStatement ps = clusteredCounterQueryMap.get(entityMeta.getEntityClass()).get(SELECT);
        BoundStatement bs = binder.bindForClusteredCounterSelect(ps, entityMeta, counterMeta,
                context.getPrimaryKey());
        ConsistencyLevel readLevel = consistencyLevel != null ? consistencyLevel : counterMeta
                .getWriteConsistencyLevel();
        ResultSet resultSet = context.executeImmediateWithConsistency(bs, readLevel);

        return returnFirstRowOrNull(resultSet.all());
    }

    public void bindForClusteredCounterDelete(CQLPersistenceContext context, EntityMeta meta,
            PropertyMeta<?, ?> counterMeta, Object primaryKey)
    {
        PreparedStatement ps = clusteredCounterQueryMap.get(meta.getEntityClass()).get(DELETE);
        BoundStatement bs = binder.bindForClusteredCounterDelete(ps, meta, counterMeta, primaryKey);
        ConsistencyLevel consistency = context.getWriteConsistencyLevel().isPresent() ? context
                .getWriteConsistencyLevel().get() : counterMeta.getWriteConsistencyLevel();
        context.pushBoundStatement(bs, consistency);
    }

    public Row eagerLoadEntity(CQLPersistenceContext context)
    {
        EntityMeta meta = context.getEntityMeta();
        Class<?> entityClass = context.getEntityClass();
        PreparedStatement ps = selectEagerPSs.get(entityClass);

        List<Row> rows = executeReadWithConsistency(context, ps, meta.getReadConsistencyLevel());
        return returnFirstRowOrNull(rows);
    }

    private List<Row> executeReadWithConsistency(CQLPersistenceContext context,
            PreparedStatement ps, ConsistencyLevel readLevel)
    {
        EntityMeta entityMeta = context.getEntityMeta();
        BoundStatement boundStatement = binder.bindStatementWithOnlyPKInWhereClause(ps, entityMeta,
                context.getPrimaryKey());

        return context.executeImmediateWithConsistency(boundStatement, readLevel).all();
    }

    private Row returnFirstRowOrNull(List<Row> rows)
    {
        if (rows.isEmpty())
        {
            return null;
        }
        else
        {
            return rows.get(0);
        }
    }

    public ResultSet execute(Query query)
    {
        logDMLStatement(query);
        return session.execute(query);
    }

    public PreparedStatement prepare(Statement statement)
    {
        return session.prepare(statement.getQueryString());
    }

    public ResultSet bindAndExecute(PreparedStatement ps, Object... params)
    {
        BoundStatement bs = ps.bind(params);

        logDMLStatement(bs);
        return session.execute(bs);

    }

    private void logDMLStatement(Query query)
    {
        if (dmlLogger.isDebugEnabled())
        {
            String queryType;
            String queryString;
            String consistencyLevel;
            if (BoundStatement.class.isInstance(query))
            {
                PreparedStatement ps = BoundStatement.class.cast(query).preparedStatement();
                queryType = "Prepared statement";
                queryString = ps.getQueryString();
                consistencyLevel = ps.getConsistencyLevel() == null ? "DEFAULT" : ps.getConsistencyLevel().name();
            }
            else if (Statement.class.isInstance(query))
            {
                Statement statement = Statement.class.cast(query);
                queryType = "Simple query";
                queryString = statement.getQueryString();
                consistencyLevel = statement.getConsistencyLevel() == null ? "DEFAULT" : statement
                        .getConsistencyLevel().name();
            }
            else
            {
                queryType = "Unknown query";
                queryString = "???";
                consistencyLevel = "UNKNWON";
            }

            dmlLogger
                    .debug("{} : [{}] with CONSISTENCY LEVEL [{}]", queryType, queryString, consistencyLevel);

        }
    }
}
