/*
 * Copyright 2018 Confluent Inc.
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

package io.confluent.ksql.planner.plan;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Streams;
import com.google.errorprone.annotations.Immutable;
import io.confluent.ksql.execution.builder.KsqlQueryBuilder;
import io.confluent.ksql.execution.expression.tree.Expression;
import io.confluent.ksql.metastore.model.DataSource.DataSourceType;
import io.confluent.ksql.metastore.model.KeyField;
import io.confluent.ksql.name.ColumnName;
import io.confluent.ksql.name.SourceName;
import io.confluent.ksql.planner.Projection;
import io.confluent.ksql.schema.ksql.Column;
import io.confluent.ksql.schema.ksql.LogicalSchema;
import io.confluent.ksql.schema.ksql.SystemColumns;
import io.confluent.ksql.services.KafkaTopicClient;
import io.confluent.ksql.structured.SchemaKStream;
import io.confluent.ksql.util.GrammaticalJoiner;
import io.confluent.ksql.util.KsqlException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Immutable
public abstract class PlanNode {

  private final PlanNodeId id;
  private final DataSourceType nodeOutputType;
  private final LogicalSchema schema;
  private final Optional<SourceName> sourceName;

  protected PlanNode(
      final PlanNodeId id,
      final DataSourceType nodeOutputType,
      final LogicalSchema schema,
      final Optional<SourceName> sourceName
  ) {
    this.id = requireNonNull(id, "id");
    this.nodeOutputType = requireNonNull(nodeOutputType, "nodeOutputType");
    this.schema = requireNonNull(schema, "schema");
    this.sourceName = requireNonNull(sourceName, "sourceName");
  }

  public final PlanNodeId getId() {
    return id;
  }

  public final DataSourceType getNodeOutputType() {
    return nodeOutputType;
  }

  public final LogicalSchema getSchema() {
    return schema;
  }

  public abstract KeyField getKeyField();

  public abstract List<PlanNode> getSources();

  public <C, R> R accept(final PlanVisitor<C, R> visitor, final C context) {
    return visitor.visitPlan(this, context);
  }

  public DataSourceNode getTheSourceNode() {
    if (this instanceof DataSourceNode) {
      return (DataSourceNode) this;
    } else if (!getSources().isEmpty()) {
      return this.getSources().get(0).getTheSourceNode();
    }
    throw new IllegalStateException("No source node in hierarchy");
  }

  protected abstract int getPartitions(KafkaTopicClient kafkaTopicClient);

  public abstract SchemaKStream<?> buildStream(KsqlQueryBuilder builder);

  Optional<SourceName> getSourceName() {
    return sourceName;
  }

  /**
   * Call to resolve an {@link io.confluent.ksql.parser.tree.AllColumns} instance into a
   * corresponding set of columns.
   *
   * @param sourceName the name of the source
   * @return the list of columns.
   */
  public Stream<ColumnName> resolveSelectStar(
      final Optional<SourceName> sourceName
  ) {
    return getSources().stream()
        .filter(s -> !sourceName.isPresent() || sourceName.equals(s.getSourceName()))
        .flatMap(s -> s.resolveSelectStar(sourceName));
  }

  /**
   * Called to resolve the supplied {@code expression} into an expression that matches the nodes
   * schema.
   *
   * <p>{@link AggregateNode} and {@link FlatMapNode} replace UDAFs and UDTFs with synthetic column
   * names. Where a select is a UDAF or UDTF this method will return the appropriate synthetic
   * {@link io.confluent.ksql.execution.expression.tree.UnqualifiedColumnReferenceExp}
   *
   *
   * @param idx the index of the select within the projection.
   * @param expression the expression to resolve.
   * @return the resolved expression.
   */
  public Expression resolveSelect(final int idx, final Expression expression) {
    return expression;
  }

  /**
   * Called to check that the query's projection contains the required key columns.
   *
   * <p>Only called for persistent queries.
   *
   * @param sinkName the name of the sink being created by the persistent query.
   * @param projection the query's projection.
   * @throws KsqlException if any key columns are missing from the projection.
   */
  void validateKeyPresent(final SourceName sinkName, final Projection projection) {
    getSources().forEach(s -> s.validateKeyPresent(sinkName, projection));
  }

  static void throwKeysNotIncludedError(
      final SourceName sinkName,
      final String type,
      final List<? extends Expression> requiredKeys,
      final GrammaticalJoiner joiner
  ) {
    throwKeysNotIncludedError(sinkName, type, requiredKeys, joiner, "");
  }

  static void throwKeysNotIncludedError(
      final SourceName sinkName,
      final String type,
      final List<? extends Expression> requiredKeys,
      final GrammaticalJoiner joiner,
      final String additional
  ) {
    final String postfix = requiredKeys.size() == 1 ? "" : "s";

    final String joined = joiner.join(requiredKeys);

    throw new KsqlException("The query used to build " + sinkName
        + " must include the " + type + postfix
        + " " + joined + " in its projection."
        + additional
    );
  }

  @SuppressWarnings("UnstableApiUsage")
  static Stream<ColumnName> orderColumns(
      final List<Column> columns,
      final LogicalSchema schema
  ) {
    // When doing a `select *` key columns should be at the front of the column list
    // but are added at the back during processing for performance reasons.
    // Switch them around here:
    final Stream<Column> keys = columns.stream()
        .filter(c -> schema.isKeyColumn(c.name()));

    final Stream<Column> windowBounds = columns.stream()
        .filter(c -> SystemColumns.isWindowBound(c.name()));

    final Stream<Column> values = columns.stream()
        .filter(c -> !SystemColumns.isWindowBound(c.name()))
        .filter(c -> !SystemColumns.isPseudoColumn(c.name()))
        .filter(c -> !schema.isKeyColumn(c.name()));

    return Streams.concat(keys, windowBounds, values).map(Column::name);
  }
}
