/**
 *
 * Copyright (C) 2012-2013 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.archinnov.achilles.context;

import static info.archinnov.achilles.type.ConsistencyLevel.EACH_QUORUM;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.util.Arrays;
import java.util.List;
import info.archinnov.achilles.context.AbstractFlushContext.FlushType;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.interceptor.Event;
import info.archinnov.achilles.interceptor.EventHolder;
import info.archinnov.achilles.statement.wrapper.AbstractStatementWrapper;
import info.archinnov.achilles.statement.wrapper.BoundStatementWrapper;
import info.archinnov.achilles.statement.wrapper.RegularStatementWrapper;
import info.archinnov.achilles.type.ConsistencyLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.internal.WhiteboxImpl;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;

@RunWith(MockitoJUnitRunner.class)
public class BatchingFlushContextTest {

	private BatchingFlushContext context;

	@Mock
	private DaoContext daoContext;

	@Mock
	private BoundStatementWrapper bsWrapper;

	@Mock
	private RegularStatement query;

    @Captor
    ArgumentCaptor<BatchStatement> batchCaptor;

	@Before
	public void setUp() {
		context = new BatchingFlushContext(daoContext, EACH_QUORUM);
	}

	@Test
	public void should_start_batch() throws Exception {
        context.startBatch();
	}

	@Test
	public void should_do_nothing_when_flush_is_called() throws Exception {
		context.statementWrappers.add(bsWrapper);

		context.flush();

		assertThat(context.statementWrappers).containsExactly(bsWrapper);
	}

	@Test
	public void should_end_batch() throws Exception {
        //Given
        EventHolder eventHolder = mock(EventHolder.class);
        RegularStatement statement = QueryBuilder.select().from("table");
        AbstractStatementWrapper wrapper = new RegularStatementWrapper(statement,null, com.datastax.driver.core
                .ConsistencyLevel.ONE);
        context.eventHolders= Arrays.asList(eventHolder);
        context.statementWrappers =Arrays.asList(wrapper);
        context.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);

        //When
		context.endBatch();

        //Then
        verify(eventHolder).triggerInterception();
        verify(daoContext).executeBatch(batchCaptor.capture());
        final BatchStatement batchStatement = batchCaptor.getValue();
        assertThat(batchStatement.getConsistencyLevel()).isSameAs(com.datastax.driver.core.ConsistencyLevel.LOCAL_QUORUM);
        final List<Statement> statements = WhiteboxImpl.getInternalState(batchStatement, "statements");
        assertThat(statements).contains(statement);
    }

	@Test
	public void should_get_type() throws Exception {
		assertThat(context.type()).isSameAs(FlushType.BATCH);
	}

	@Test
	public void should_duplicate_without_ttl() throws Exception {
		context.statementWrappers.add(bsWrapper);

		BatchingFlushContext duplicate = context.duplicate();

		assertThat(duplicate.statementWrappers).containsOnly(bsWrapper);
		assertThat(duplicate.consistencyLevel).isSameAs(EACH_QUORUM);
	}

    @Test
    public void should_trigger_interceptor_immediately_for_POST_LOAD_event() throws Exception {
        //Given
        EntityMeta meta = mock(EntityMeta.class);
        Object entity = new Object();

        //When
        context.triggerInterceptor(meta,entity, Event.POST_LOAD);

        //Then
        verify(meta).intercept(entity,Event.POST_LOAD);
    }

    @Test
    public void should_push_interceptor_to_list() throws Exception {
        //Given
        EntityMeta meta = mock(EntityMeta.class);
        Object entity = new Object();

        //When
        context.triggerInterceptor(meta,entity, Event.POST_PERSIST);

        //Then
        verify(meta,never()).intercept(entity,Event.POST_PERSIST);
        assertThat(context.eventHolders).hasSize(1);
        final EventHolder eventHolder = context.eventHolders.get(0);
        eventHolder.triggerInterception();
        verify(meta).intercept(entity,Event.POST_PERSIST);
    }

    @Test
    public void should_duplicate_with_no_data() throws Exception {
        //Given
        context.statementWrappers.add(mock(AbstractStatementWrapper.class));
        context.eventHolders.add(mock(EventHolder.class));

        //When
        final BatchingFlushContext newContext = context.duplicateWithNoData(ConsistencyLevel.EACH_QUORUM);

        //Then
        assertThat(newContext.statementWrappers).isEmpty();
        assertThat(newContext.eventHolders).isEmpty();

    }
}
