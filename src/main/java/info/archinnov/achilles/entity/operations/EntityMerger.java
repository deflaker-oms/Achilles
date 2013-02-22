package info.archinnov.achilles.entity.operations;

import static info.archinnov.achilles.entity.metadata.PropertyType.JOIN_SIMPLE;
import static info.archinnov.achilles.entity.metadata.PropertyType.SIMPLE;
import static javax.persistence.CascadeType.ALL;
import static javax.persistence.CascadeType.MERGE;
import info.archinnov.achilles.dao.GenericDynamicCompositeDao;
import info.archinnov.achilles.entity.EntityHelper;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.JoinProperties;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.proxy.builder.EntityProxyBuilder;
import info.archinnov.achilles.proxy.interceptor.JpaEntityInterceptor;
import info.archinnov.achilles.validation.Validator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.persistence.CascadeType;

import me.prettyprint.hector.api.mutation.Mutator;
import net.sf.cglib.proxy.Factory;

/**
 * EntityMerger
 * 
 * @author DuyHai DOAN
 * 
 */
public class EntityMerger
{
	private EntityPersister persister = new EntityPersister();
	private EntityProxyBuilder interceptorBuilder = new EntityProxyBuilder();
	private EntityHelper helper = new EntityHelper();

	@SuppressWarnings("unchecked")
	public <T, ID> T mergeEntity(T entity, EntityMeta<ID> entityMeta)
	{
		Validator.validateNotNull(entity, "Proxy object should not be null");
		Validator.validateNotNull(entityMeta, "entityMeta should not be null");

		T proxy;
		if (helper.isProxy(entity))
		{
			Factory factory = (Factory) entity;
			JpaEntityInterceptor<ID> interceptor = (JpaEntityInterceptor<ID>) factory
					.getCallback(0);
			GenericDynamicCompositeDao<ID> dao = entityMeta.getEntityDao();

			Map<Method, PropertyMeta<?, ?>> dirtyMap = interceptor.getDirtyMap();

			Object realObject = interceptor.getTarget();

			if (dirtyMap.size() > 0)
			{
				Mutator<ID> mutator = dao.buildMutator();

				for (Entry<Method, PropertyMeta<?, ?>> entry : dirtyMap.entrySet())
				{
					PropertyMeta<?, ?> propertyMeta = entry.getValue();
					ID key = interceptor.getKey();
					if (propertyMeta.type() != SIMPLE || propertyMeta.type() != JOIN_SIMPLE)
					{
						this.persister.removePropertyBatch(key, dao, propertyMeta, mutator);
					}
					this.persister.persistProperty(realObject, key, dao, propertyMeta, mutator);
				}

				mutator.execute();
			}

			dirtyMap.clear();

			for (Entry<String, PropertyMeta<?, ?>> entry : entityMeta.getPropertyMetas().entrySet())
			{

				PropertyMeta<?, ?> propertyMeta = entry.getValue();

				if (propertyMeta.type().isJoinColumn())
				{
					List<CascadeType> cascadeTypes = propertyMeta.getJoinProperties()
							.getCascadeTypes();
					if (cascadeTypes.contains(MERGE) || cascadeTypes.contains(ALL))
					{
						switch (propertyMeta.type())
						{
							case JOIN_SIMPLE:
								mergeJoinProperty(entity, propertyMeta);
								break;
							case JOIN_LIST:
								mergeJoinListProperty(entity, propertyMeta);
								break;
							case JOIN_SET:
								mergeJoinSetProperty(entity, propertyMeta);
								break;
							case JOIN_MAP:
								mergeJoinMapProperty(entity, propertyMeta);
								break;
							default:
								break;
						}
					}
				}
			}
			proxy = entity;
		}
		else
		{
			this.persister.persist(entity, entityMeta);
			proxy = (T) interceptorBuilder.build(entity, entityMeta);
		}

		return proxy;
	}

	@SuppressWarnings("unchecked")
	private <T> void mergeJoinProperty(T entity, PropertyMeta<?, ?> propertyMeta)
	{
		JoinProperties joinProperties = propertyMeta.getJoinProperties();
		Object joinEntity = helper.getValueFromField(entity, propertyMeta.getGetter());
		if (joinEntity != null)
		{
			Object mergedEntity = this.mergeEntity(joinEntity, joinProperties.getEntityMeta());
			helper.setValueToField(entity, propertyMeta.getSetter(), mergedEntity);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void mergeJoinListProperty(T entity, PropertyMeta<?, ?> propertyMeta)
	{
		JoinProperties joinProperties = propertyMeta.getJoinProperties();
		List<?> joinEntities = (List<?>) helper.getValueFromField(entity, propertyMeta.getGetter());
		List<Object> mergedEntities = new ArrayList<Object>();
		if (joinEntities != null)
		{
			for (Object joinEntity : joinEntities)
			{
				Object mergedEntity = this.mergeEntity(joinEntity, joinProperties.getEntityMeta());
				mergedEntities.add(mergedEntity);
			}
		}
		helper.setValueToField(entity, propertyMeta.getSetter(), mergedEntities);
	}

	@SuppressWarnings("unchecked")
	private <T> void mergeJoinSetProperty(T entity, PropertyMeta<?, ?> propertyMeta)
	{
		JoinProperties joinProperties = propertyMeta.getJoinProperties();
		Set<?> joinEntities = (Set<?>) helper.getValueFromField(entity, propertyMeta.getGetter());
		Set<Object> mergedEntities = new HashSet<Object>();
		if (joinEntities != null)
		{
			for (Object joinEntity : joinEntities)
			{
				Object mergedEntity = this.mergeEntity(joinEntity, joinProperties.getEntityMeta());
				mergedEntities.add(mergedEntity);
			}
		}
		helper.setValueToField(entity, propertyMeta.getSetter(), mergedEntities);
	}

	@SuppressWarnings("unchecked")
	private <T> void mergeJoinMapProperty(T entity, PropertyMeta<?, ?> propertyMeta)
	{
		JoinProperties joinProperties = propertyMeta.getJoinProperties();
		Map<?, ?> joinEntitiesMap = (Map<?, ?>) helper.getValueFromField(entity,
				propertyMeta.getGetter());
		Map<Object, Object> mergedEntitiesMap = new HashMap<Object, Object>();
		if (joinEntitiesMap != null)
		{
			for (Entry<?, ?> joinEntityEntry : joinEntitiesMap.entrySet())
			{
				Object mergedEntity = this.mergeEntity(joinEntityEntry.getValue(),
						joinProperties.getEntityMeta());
				mergedEntitiesMap.put(joinEntityEntry.getKey(), mergedEntity);
			}
		}
		helper.setValueToField(entity, propertyMeta.getSetter(), mergedEntitiesMap);
	}

	public void setPersister(EntityPersister persister)
	{
		this.persister = persister;
	}
}