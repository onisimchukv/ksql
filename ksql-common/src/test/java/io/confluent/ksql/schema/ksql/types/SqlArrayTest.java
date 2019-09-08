/*
 * Copyright 2019 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.schema.ksql.types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import io.confluent.ksql.schema.ksql.DataException;
import io.confluent.ksql.schema.ksql.SqlBaseType;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SqlArrayTest {

  private static final SqlType SOME_TYPE = SqlPrimitiveType.of(SqlBaseType.STRING);

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldImplementHashCodeAndEqualsProperly() {
    new EqualsTester()
        .addEqualityGroup(SqlArray.of(SOME_TYPE), SqlArray.of(SOME_TYPE))
        .addEqualityGroup(SqlArray.of(SqlPrimitiveType.of(SqlBaseType.BOOLEAN)))
        .addEqualityGroup(SqlMap.of(SqlPrimitiveType.of(SqlBaseType.BOOLEAN)))
        .testEquals();
  }

  @Test
  public void shouldReturnSqlType() {
    assertThat(SqlArray.of(SOME_TYPE).baseType(), is(SqlBaseType.ARRAY));
  }

  @Test
  public void shouldReturnValueType() {
    assertThat(SqlArray.of(SOME_TYPE).getItemType(), is(SOME_TYPE));
  }

  @Test
  public void shouldImplmentToString() {
    assertThat(SqlArray.of(SOME_TYPE).toString(), is(
        "ARRAY<"
            + SOME_TYPE.toString()
            + ">"
    ));
  }

  @Test
  public void shouldThrowIfNotValueList() {
    // Given:
    final SqlArray schema = SqlTypes.array(SqlTypes.BIGINT);

    // Then:
    expectedException.expect(DataException.class);
    expectedException.expectMessage("Expected ARRAY, got BIGINT");

    // When:
    schema.validateValue(10L);
  }

  @Test
  public void shouldThrowIfAnyElementInValueNotElementType() {
    // Given:
    final SqlArray schema = SqlTypes.array(SqlTypes.BIGINT);

    // Then:
    expectedException.expect(DataException.class);
    expectedException.expectMessage("ARRAY element 2: Expected BIGINT, got INT");

    // When:
    schema.validateValue(ImmutableList.of(11L, 9));
  }

  @Test
  public void shouldNotThrowWhenValidatingNullValue() {
    // Given:
    final SqlArray schema = SqlTypes.array(SqlTypes.BIGINT);

    // When:
    schema.validateValue(null);
  }

  @Test
  public void shouldValidateValue() {
    // Given:
    final SqlArray schema = SqlTypes.array(SqlTypes.BIGINT);

    // When:
    schema.validateValue(Arrays.asList(19L, null));
  }
}