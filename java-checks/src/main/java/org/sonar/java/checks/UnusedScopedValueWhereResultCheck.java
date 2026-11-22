/*
 * SonarQube Java
 * Copyright (C) 2012-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.java.checks;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.MethodMatchers;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.Arguments;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.ConditionalExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LambdaExpressionTree;
import org.sonar.plugins.java.api.tree.ListTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.NewArrayTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeCastTree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.plugins.java.api.tree.YieldStatementTree;

@Rule(key = "S8432")
public class UnusedScopedValueWhereResultCheck extends IssuableSubscriptionVisitor {

  private static final String WHERE_METHOD_NAME = "where";

  private static final String MESSAGE = "Use this ScopedValue.Carrier by calling run() or call(), or remove this useless call to where().";

  private static final Set<String> CONSUMPTION_METHODS = Set.of("run", "call");

  private static final MethodMatchers WHERE_MATCHER = MethodMatchers.or(
    MethodMatchers.create()
      .ofTypes("java.lang.ScopedValue")
      .names(WHERE_METHOD_NAME)
      .withAnyParameters()
      .build(),
    MethodMatchers.create()
      .ofTypes("java.lang.ScopedValue$Carrier")
      .names(WHERE_METHOD_NAME)
      .withAnyParameters()
      .build());

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return List.of(Tree.Kind.METHOD_INVOCATION, Tree.Kind.VARIABLE);
  }

  @Override
  public void visitNode(Tree tree) {
    if (tree instanceof MethodInvocationTree methodInvocationTree) {
      checkMethodInvocation(methodInvocationTree);
    } else if (tree instanceof VariableTree variableTree) {
      checkCarrierVariable(variableTree);
    }
  }

  private void checkMethodInvocation(MethodInvocationTree mit) {
    if (!WHERE_MATCHER.matches(mit)) {
      return;
    }

    // Look through ternaries, parentheses and casts: the enclosing expression decides what happens to the carrier
    Tree node = mit;
    Tree parent = mit.parent();
    while (isTransparentParent(parent, node)) {
      node = parent;
      parent = parent.parent();
    }

    // Special case: if there is no parent, consider it consumed to avoid false positives
    if (parent == null) {
      return;
    }

    // Check if result is immediately consumed (chained .run() or .call())
    if (isImmediatelyConsumed(parent)) {
      return;
    }

    // Check if result is chained with another .where() - don't report, the final result will be checked
    if (isChainedWhere(parent)) {
      return;
    }

    // Check if result is assigned to a variable - the variable check will handle this
    if (parent instanceof VariableTree) {
      return;
    }

    // Check if result is assigned to a field (escapes)
    if (parent instanceof AssignmentExpressionTree) {
      return;
    }

    // Check if result escapes (returned or passed as argument)
    if (isEscaping(node)) {
      return;
    }

    // If we reach here, the result is discarded
    reportIssue(mit, MESSAGE);
  }

  private void checkCarrierVariable(VariableTree variableTree) {
    Symbol symbol = variableTree.symbol();
    // Fields can be used from anywhere (other files, qualified names), so only locals and parameters are analyzed
    if (!symbol.isVariableSymbol() || !(symbol.isLocalVariable() || symbol.isParameter())) {
      return;
    }

    // Check if this variable is initialized with a where() call
    boolean isWhereResult = isWhereResult(variableTree.initializer());

    // Check if this variable is a Carrier type parameter of a method with a body
    boolean isCarrierParameter = symbol.isParameter() && isCarrierType(symbol) && hasBody(variableTree);

    if (!isWhereResult && !isCarrierParameter) {
      return;
    }

    // Check all usages of the variable
    Symbol.VariableSymbol variableSymbol = (Symbol.VariableSymbol) symbol;
    List<IdentifierTree> usages = variableSymbol.usages();

    // Check if variable is reassigned before proper use
    if (isWhereResult && isReassignedBeforeProperUse(variableTree, usages)) {
      reportIssue(variableTree.simpleName(), MESSAGE);
      return;
    }

    boolean isProperlyUsed = usages.stream().anyMatch(this::isUsageValid);

    if (!isProperlyUsed) {
      reportIssue(variableTree.simpleName(), MESSAGE);
    }
  }

  private boolean isReassignedBeforeProperUse(VariableTree declaration, List<IdentifierTree> usages) {
    for (IdentifierTree usage : usages) {
      // Check if this usage is a reassignment (left side of assignment)
      Tree parent = usage.parent();
      if (parent instanceof AssignmentExpressionTree assignment
        && assignment.variable() == usage
        && isUnconditionalReassignment(declaration, assignment)
        && !isReadInside(assignment.expression(), usages)) {
        // This is a reassignment that always runs and does not read the variable - check if there's any proper use before it
        boolean hasProperUseBefore = usages.stream()
          .filter(u -> u != usage)
          .filter(u -> isBefore(u, usage))
          .anyMatch(this::isUsageValid);
        if (!hasProperUseBefore) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * A reassignment that is a statement of the same block as the declaration always runs once the declaration was executed.
   * Reassignments in branches or loops may be skipped, so the initial carrier may still be in use.
   */
  private static boolean isUnconditionalReassignment(VariableTree declaration, AssignmentExpressionTree assignment) {
    return assignment.parent() instanceof ExpressionStatementTree statement
      && declaration.parent() instanceof BlockTree
      && statement.parent() == declaration.parent();
  }

  /**
   * A reassignment such as <code>c = c.where(K, v)</code> reads the variable, which counts as using it.
   */
  private static boolean isReadInside(Tree expression, List<IdentifierTree> usages) {
    return usages.stream().anyMatch(u -> isDescendantOf(u, expression));
  }

  private static boolean isDescendantOf(Tree tree, Tree ancestor) {
    Tree current = tree;
    while (current != null) {
      if (current == ancestor) {
        return true;
      }
      current = current.parent();
    }
    return false;
  }

  private static boolean isBefore(IdentifierTree first, IdentifierTree second) {
    return first.identifierToken().range().start().isBefore(second.identifierToken().range().start());
  }

  private static boolean isWhereResult(@Nullable ExpressionTree expression) {
    if (expression instanceof MethodInvocationTree mit) {
      return WHERE_MATCHER.matches(mit);
    }
    if (expression instanceof ParenthesizedTree parenthesized) {
      return isWhereResult(parenthesized.expression());
    }
    if (expression instanceof TypeCastTree typeCast) {
      return isWhereResult(typeCast.expression());
    }
    if (expression instanceof ConditionalExpressionTree conditional) {
      return isWhereResult(conditional.trueExpression()) || isWhereResult(conditional.falseExpression());
    }
    return false;
  }

  private static boolean isTransparentParent(@Nullable Tree parent, Tree child) {
    return parent instanceof ParenthesizedTree
      || parent instanceof TypeCastTree
      || (parent instanceof ConditionalExpressionTree conditional && conditional.condition() != child);
  }

  private static boolean hasBody(VariableTree parameter) {
    Symbol owner = parameter.symbol().owner();
    if (owner instanceof Symbol.MethodSymbol methodSymbol) {
      MethodTree declaration = methodSymbol.declaration();
      return declaration == null || declaration.block() != null;
    }
    return true;
  }

  private static boolean isCarrierType(Symbol symbol) {
    return symbol.type().is("java.lang.ScopedValue$Carrier");
  }

  private boolean isUsageValid(IdentifierTree usage) {
    Tree parent = usage.parent();

    // Check if usage is a reassignment (left side of assignment) - not a valid use
    if (parent instanceof AssignmentExpressionTree assignment && assignment.variable() == usage) {
      return false;
    }

    Tree node = usage;
    while (isTransparentParent(parent, node)) {
      node = parent;
      parent = parent.parent();
    }

    // Check if usage is consumed via .run() or .call()
    if (parent instanceof MemberSelectExpressionTree memberSelect && memberSelect.expression() == node) {
      String methodName = memberSelect.identifier().name();
      if (CONSUMPTION_METHODS.contains(methodName)) {
        return true;
      }
      // Check if it's a chained .where() that eventually gets consumed
      if (WHERE_METHOD_NAME.equals(methodName)) {
        Tree grandParent = memberSelect.parent();
        if (grandParent instanceof MethodInvocationTree chainedMit) {
          return isChainEventuallyConsumed(chainedMit);
        }
      }
    }

    // Check if usage is assigned to another variable (aliasing)
    if (parent instanceof VariableTree varTree) {
      Symbol targetSymbol = varTree.symbol();
      // If assigned to a field, it escapes
      if (!targetSymbol.isLocalVariable()) {
        return true;
      }
      // If assigned to a local variable, check if that variable is properly used
      if (targetSymbol.isVariableSymbol()) {
        return targetSymbol.usages().stream().anyMatch(this::isUsageValid);
      }
    }

    // Check if usage escapes (returned or passed as argument)
    return isEscaping(node);
  }

  private static boolean isImmediatelyConsumed(Tree parent) {
    if (parent instanceof MemberSelectExpressionTree memberSelect) {
      return CONSUMPTION_METHODS.contains(memberSelect.identifier().name());
    }
    return false;
  }

  private static boolean isChainedWhere(Tree parent) {
    if (parent instanceof MemberSelectExpressionTree memberSelect) {
      return WHERE_METHOD_NAME.equals(memberSelect.identifier().name());
    }
    return false;
  }

  private boolean isChainEventuallyConsumed(MethodInvocationTree mit) {
    Tree node = mit;
    Tree parent = mit.parent();
    while (isTransparentParent(parent, node)) {
      node = parent;
      parent = parent.parent();
    }

    // Special case: if there is no parent, consider it consumed to avoid false positives
    if (parent == null) {
      return true;
    }

    if (isImmediatelyConsumed(parent)) {
      return true;
    }

    if (isChainedWhere(parent) && parent.parent() instanceof MethodInvocationTree nextMit) {
      return isChainEventuallyConsumed(nextMit);
    }

    if (isEscaping(node)) {
      return true;
    }

    if (parent instanceof VariableTree varTree) {
      Symbol symbol = varTree.symbol();
      if (symbol.isVariableSymbol()) {
        return symbol.usages().stream().anyMatch(this::isUsageValid);
      }
    }

    return false;
  }

  private static boolean isEscaping(Tree tree) {
    Tree child = tree;
    Tree parent = tree.parent();
    while (parent instanceof ParenthesizedTree || parent instanceof TypeCastTree) {
      child = parent;
      parent = parent.parent();
    }

    if (parent == null) {
      return false;
    }

    // Returned from method, or yielded from a switch expression
    if (parent instanceof ReturnStatementTree || parent instanceof YieldStatementTree) {
      return true;
    }

    // Passed as argument to another method or constructor
    if (parent instanceof Arguments) {
      return true;
    }

    // Stored into an array
    if (parent instanceof NewArrayTree || (parent instanceof ListTree<?> && parent.parent() instanceof NewArrayTree)) {
      return true;
    }

    // Stored into a variable or field by assignment (right-hand side)
    if (parent instanceof AssignmentExpressionTree assignment) {
      return assignment.expression() == child;
    }

    // Returned from an expression lambda
    if (parent instanceof LambdaExpressionTree lambda) {
      return lambda.body() == child;
    }

    // Used in ternary expression - check if the ternary escapes
    if (parent instanceof ConditionalExpressionTree) {
      return isEscaping(parent);
    }

    // Passed to constructor
    if (parent instanceof NewClassTree newClass) {
      return newClass.arguments().contains(child);
    }

    return false;
  }
}
