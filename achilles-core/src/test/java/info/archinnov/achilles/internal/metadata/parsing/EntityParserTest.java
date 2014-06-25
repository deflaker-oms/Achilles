/*
 * Copyright (C) 2012-2014 DuyHai DOAN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package info.archinnov.achilles.internal.metadata.parsing;

import static info.archinnov.achilles.internal.metadata.holder.PropertyType.COUNTER;
import static info.archinnov.achilles.internal.metadata.holder.PropertyType.EMBEDDED_ID;
import static info.archinnov.achilles.internal.metadata.holder.PropertyType.ID;
import static info.archinnov.achilles.internal.metadata.holder.PropertyType.SIMPLE;
import static org.fest.assertions.api.Assertions.assertThat;
import java.util.Arrays;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.archinnov.achilles.exception.AchillesBeanMappingException;
import info.archinnov.achilles.internal.context.ConfigurationContext;
import info.archinnov.achilles.internal.metadata.holder.CounterProperties;
import info.archinnov.achilles.internal.metadata.holder.EntityMeta;
import info.archinnov.achilles.internal.metadata.holder.PropertyMeta;
import info.archinnov.achilles.internal.metadata.holder.PropertyType;
import info.archinnov.achilles.internal.metadata.parsing.context.EntityParsingContext;
import info.archinnov.achilles.json.ObjectMapperFactory;
import info.archinnov.achilles.test.parser.entity.Bean;
import info.archinnov.achilles.test.parser.entity.BeanWithClusteredId;
import info.archinnov.achilles.test.parser.entity.BeanWithColumnFamilyName;
import info.archinnov.achilles.test.parser.entity.BeanWithDuplicatedColumnName;
import info.archinnov.achilles.test.parser.entity.BeanWithIdAndColumnAnnotationsOnSameField;
import info.archinnov.achilles.test.parser.entity.BeanWithInsertStrategy;
import info.archinnov.achilles.test.parser.entity.BeanWithNoId;
import info.archinnov.achilles.test.parser.entity.BeanWithSimpleCounter;
import info.archinnov.achilles.test.parser.entity.ChildBean;
import info.archinnov.achilles.test.parser.entity.ClusteredEntity;
import info.archinnov.achilles.test.parser.entity.EmbeddedKey;
import info.archinnov.achilles.test.parser.entity.UserBean;
import info.archinnov.achilles.type.ConsistencyLevel;
import info.archinnov.achilles.type.Counter;
import info.archinnov.achilles.type.InsertStrategy;

@RunWith(MockitoJUnitRunner.class)
public class EntityParserTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private EntityParser parser = new EntityParser();

    private ConfigurationContext configContext = new ConfigurationContext();

    @Mock
    private Map<Class<?>, EntityMeta> entityMetaMap;

    private ObjectMapperFactory objectMapperFactory = new ObjectMapperFactory() {
        @Override
        public <T> ObjectMapper getMapper(Class<T> type) {
            return objectMapper;
        }
    };
    private ObjectMapper objectMapper = new ObjectMapper();

    private EntityParsingContext entityContext;

    @Before
    public void setUp() {
        configContext.setDefaultReadConsistencyLevel(ConsistencyLevel.ONE);
        configContext.setDefaultWriteConsistencyLevel(ConsistencyLevel.ALL);
        configContext.setEnableSchemaUpdateForTables(Arrays.<String>asList());
        configContext.setObjectMapperFactory(objectMapperFactory);
        configContext.setInsertStrategy(InsertStrategy.ALL_FIELDS);
    }

    @Test
    public void should_parse_entity() throws Exception {

        configContext.setEnableSchemaUpdate(true);
        initEntityParsingContext(Bean.class);
        EntityMeta meta = parser.parseEntity(entityContext);

        assertThat(meta.getClassName()).isEqualTo("info.archinnov.achilles.test.parser.entity.Bean");
        assertThat(meta.getTableName()).isEqualTo("Bean");
        assertThat(meta.getIdMeta().<Long>getValueClass()).isEqualTo(Long.class);
        assertThat(meta.getIdMeta().getPropertyName()).isEqualTo("id");
        assertThat(meta.<Long>getIdClass()).isEqualTo(Long.class);
        assertThat(meta.getPropertyMetas()).hasSize(8);

        PropertyMeta id = meta.getPropertyMetas().get("id");
        PropertyMeta name = meta.getPropertyMetas().get("name");
        PropertyMeta age = meta.getPropertyMetas().get("age_in_year");
        PropertyMeta friends = meta.getPropertyMetas().get("friends");
        PropertyMeta followers = meta.getPropertyMetas().get("followers");
        PropertyMeta preferences = meta.getPropertyMetas().get("preferences");

        PropertyMeta creator = meta.getPropertyMetas().get("creator");
        PropertyMeta count = meta.getPropertyMetas().get("count");

        assertThat(id).isNotNull();
        assertThat(name).isNotNull();
        assertThat(age).isNotNull();
        assertThat(friends).isNotNull();
        assertThat(followers).isNotNull();
        assertThat(preferences).isNotNull();
        assertThat(creator).isNotNull();
        assertThat(count).isNotNull();

        assertThat(id.getPropertyName()).isEqualTo("id");
        assertThat(id.<Long>getValueClass()).isEqualTo(Long.class);
        assertThat(id.type()).isEqualTo(ID);
        assertThat(id.getReadConsistencyLevel()).isEqualTo(ConsistencyLevel.ONE);
        assertThat(id.getWriteConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);

        assertThat(name.getPropertyName()).isEqualTo("name");
        assertThat(name.<String>getValueClass()).isEqualTo(String.class);
        assertThat(name.type()).isEqualTo(SIMPLE);
        assertThat(name.getReadConsistencyLevel()).isEqualTo(ConsistencyLevel.ONE);
        assertThat(name.getWriteConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);

        assertThat(age.getPropertyName()).isEqualTo("age_in_year");
        assertThat(age.<Long>getValueClass()).isEqualTo(Long.class);
        assertThat(age.type()).isEqualTo(SIMPLE);
        assertThat(age.getReadConsistencyLevel()).isEqualTo(ConsistencyLevel.ONE);
        assertThat(age.getWriteConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);

        assertThat(friends.getPropertyName()).isEqualTo("friends");
        assertThat(friends.<String>getValueClass()).isEqualTo(String.class);
        assertThat(friends.type()).isEqualTo(PropertyType.LIST);
        assertThat(friends.getReadConsistencyLevel()).isEqualTo(ConsistencyLevel.ONE);
        assertThat(friends.getWriteConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);

        assertThat(followers.getPropertyName()).isEqualTo("followers");
        assertThat(followers.<String>getValueClass()).isEqualTo(String.class);
        assertThat(followers.type()).isEqualTo(PropertyType.SET);
        assertThat(followers.getReadConsistencyLevel()).isEqualTo(ConsistencyLevel.ONE);
        assertThat(followers.getWriteConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);

        assertThat(preferences.getPropertyName()).isEqualTo("preferences");
        assertThat(preferences.<String>getValueClass()).isEqualTo(String.class);
        assertThat(preferences.type()).isEqualTo(PropertyType.MAP);
        assertThat(preferences.<Integer>getKeyClass()).isEqualTo(Integer.class);
        assertThat(preferences.getReadConsistencyLevel()).isEqualTo(ConsistencyLevel.ONE);
        assertThat(preferences.getWriteConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);

        assertThat(creator.getPropertyName()).isEqualTo("creator");
        assertThat(creator.<UserBean>getValueClass()).isEqualTo(UserBean.class);
        assertThat(creator.type()).isEqualTo(SIMPLE);

        assertThat(count.getPropertyName()).isEqualTo("count");
        assertThat(count.<Counter>getValueClass()).isEqualTo(Counter.class);
        assertThat(count.type()).isEqualTo(COUNTER);

        assertThat(meta.getReadConsistencyLevel()).isEqualTo(ConsistencyLevel.ONE);
        assertThat(meta.getWriteConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);

        assertThat(meta.getAllMetasExceptIdAndCounters()).hasSize(6).containsOnly(name, age, friends, followers, preferences, creator);
        assertThat(meta.getAllMetasExceptCounters()).hasSize(7).containsOnly(id, name, age, friends, followers, preferences, creator);

        assertThat(meta.getInsertStrategy()).isEqualTo(InsertStrategy.ALL_FIELDS);
        assertThat(meta.isSchemaUpdateEnabled()).isTrue();
    }

    @Test
    public void should_parse_entity_with_embedded_id() throws Exception {
        initEntityParsingContext(BeanWithClusteredId.class);

        EntityMeta meta = parser.parseEntity(entityContext);

        assertThat(meta).isNotNull();

        assertThat(meta.<EmbeddedKey>getIdClass()).isEqualTo(EmbeddedKey.class);
        PropertyMeta idMeta = meta.getIdMeta();

        assertThat(idMeta.isEmbeddedId()).isTrue();
        assertThat(idMeta.getComponentClasses()).containsExactly(Long.class, String.class);

    }

    @Test
    public void should_parse_entity_with_table_name() throws Exception {

        initEntityParsingContext(BeanWithColumnFamilyName.class);

        EntityMeta meta = parser.parseEntity(entityContext);

        assertThat(meta).isNotNull();
        assertThat(meta.getTableName()).isEqualTo("myOwnCF");
    }

    @Test
    public void should_parse_inherited_bean() throws Exception {
        initEntityParsingContext(ChildBean.class);
        EntityMeta meta = parser.parseEntity(entityContext);

        assertThat(meta).isNotNull();
        assertThat(meta.getIdMeta().getPropertyName()).isEqualTo("id");
        assertThat(meta.getPropertyMetas().get("name").getPropertyName()).isEqualTo("name");
        assertThat(meta.getPropertyMetas().get("address").getPropertyName()).isEqualTo("address");
        assertThat(meta.getPropertyMetas().get("nickname").getPropertyName()).isEqualTo("nickname");
    }

    @Test
    public void should_parse_bean_with_simple_counter_field() throws Exception {
        initEntityParsingContext(BeanWithSimpleCounter.class);
        EntityMeta meta = parser.parseEntity(entityContext);

        assertThat(meta).isNotNull();
        assertThat(entityContext.hasSimpleCounter()).isTrue();
        PropertyMeta idMeta = meta.getIdMeta();
        assertThat(idMeta).isNotNull();
        PropertyMeta counterMeta = meta.getPropertyMetas().get("counter");
        assertThat(counterMeta).isNotNull();

        CounterProperties counterProperties = counterMeta.getCounterProperties();

        assertThat(counterProperties).isNotNull();
        assertThat(counterProperties.getFqcn()).isEqualTo(BeanWithSimpleCounter.class.getCanonicalName());
        assertThat(counterProperties.getIdMeta()).isSameAs(idMeta);
    }

    @Test
    public void should_parse_bean_with_id_and_column_annotation_on_same_field() throws Exception {
        // Given
        initEntityParsingContext(BeanWithIdAndColumnAnnotationsOnSameField.class);

        // When
        EntityMeta meta = parser.parseEntity(entityContext);

        // Then
        assertThat(meta).isNotNull();
        assertThat(meta.getIdMeta().getPropertyName()).isEqualTo("toto");
    }

    @Test
    public void should_exception_when_entity_has_no_id() throws Exception {
        initEntityParsingContext(BeanWithNoId.class);

        expectedEx.expect(AchillesBeanMappingException.class);
        expectedEx.expectMessage("The entity '" + BeanWithNoId.class.getCanonicalName()
                + "' should have at least one field with javax.persistence.Id/javax.persistence.EmbeddedId annotation");
        parser.parseEntity(entityContext);
    }

    @Test
    public void should_exception_when_entity_has_duplicated_column_name() throws Exception {
        initEntityParsingContext(BeanWithDuplicatedColumnName.class);
        expectedEx.expect(AchillesBeanMappingException.class);
        expectedEx.expectMessage("The property 'name' is already used for the entity '"
                + BeanWithDuplicatedColumnName.class.getCanonicalName() + "'");

        parser.parseEntity(entityContext);
    }

    @Test
    public void should_parse_wide_row() throws Exception {
        initEntityParsingContext(ClusteredEntity.class);
        EntityMeta meta = parser.parseEntity(entityContext);

        assertThat(meta.isClusteredEntity()).isTrue();

        assertThat(meta.getIdMeta().getPropertyName()).isEqualTo("id");
        assertThat(meta.getIdMeta().<EmbeddedKey>getValueClass()).isEqualTo(EmbeddedKey.class);

        assertThat(meta.getPropertyMetas()).hasSize(2);
        assertThat(meta.getPropertyMetas().get("id").type()).isEqualTo(EMBEDDED_ID);
        assertThat(meta.getPropertyMetas().get("value").type()).isEqualTo(SIMPLE);
    }

    @Test
    public void should_parse_bean_with_insert_strategy() throws Exception {
        //Given
        initEntityParsingContext(BeanWithInsertStrategy.class);

        //When
        EntityMeta meta = parser.parseEntity(entityContext);

        //Then
        assertThat(meta.getInsertStrategy()).isEqualTo(InsertStrategy.NOT_NULL_FIELDS);

    }

    @Test
    public void should_parse_entity_with_scheme_update_enabled() throws Exception {
        initEntityParsingContext(BeanWithClusteredId.class);

        configContext.setEnableSchemaUpdate(false);
        configContext.setEnableSchemaUpdateForTables(Arrays.asList("BeanWithClusteredId"));
        EntityMeta meta = parser.parseEntity(entityContext);

        assertThat(meta.isSchemaUpdateEnabled()).isTrue();
    }

    private <T> void initEntityParsingContext(Class<T> entityClass) {
        entityContext = new EntityParsingContext(configContext, entityClass);
    }
}
