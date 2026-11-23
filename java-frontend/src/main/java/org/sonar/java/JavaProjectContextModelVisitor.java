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
package org.sonar.java;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.java.model.declaration.ClassTreeImpl;
import org.sonar.plugins.java.api.InputFileScannerContext;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.Tree;

public class JavaProjectContextModelVisitor extends IssuableSubscriptionVisitor {
  private static final String[] SPRING_BEAN_ANNOTATIONS = {
    "org.springframework.stereotype.Component"
  };

  private final ProjectContextModel projectContextModel;

  public JavaProjectContextModelVisitor(ProjectContextModel projectContextModel) {
    this.projectContextModel = projectContextModel;
  }

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return List.of(Tree.Kind.CLASS);
  }

  @Override
  public void visitNode(Tree tree) {
    if (tree instanceof ClassTreeImpl classTree) {
      visitClass(classTree);
    }
  }

  private void visitClass(ClassTreeImpl classTree) {
    if (classTree.modifiers().annotations().stream()
      .anyMatch(a -> a.symbolType().is("org.springframework.stereotype.Component"))) {
      projectContextModel.springComponents.add(classTree.symbol().type().fullyQualifiedName());
    }

    SymbolMetadata classSymbolMetadata = classTree.symbol().metadata();
    // Spring's component scan only registers concrete classes: abstract classes and interfaces are never beans.
    if (!classTree.symbol().isAbstract() && isSpringBean(classSymbolMetadata)) {
      Symbol.TypeSymbol symbol = classTree.symbol();
      Set<String> types = getTypes(symbol);
      for (String type : types) {
        Set<String> impls = projectContextModel.availableImpls.computeIfAbsent(type, k -> new HashSet<>());
        impls.add(symbol.type().fullyQualifiedName());
      }
    }
  }

  /**
   * A class is a Spring bean when it is annotated with {@code @Component} or with an annotation
   * that is itself (transitively) meta-annotated with it, such as {@code @Service} or {@code @RestController}.
   */
  private static boolean isSpringBean(SymbolMetadata metadata) {
    return isSpringBean(metadata, new HashSet<>());
  }

  private static boolean isSpringBean(SymbolMetadata metadata, Set<String> visited) {
    if (Arrays.stream(SPRING_BEAN_ANNOTATIONS).anyMatch(metadata::isAnnotatedWith)) {
      return true;
    }
    for (SymbolMetadata.AnnotationInstance annotation : metadata.annotations()) {
      Symbol annotationSymbol = annotation.symbol();
      if (annotationSymbol.isUnknown() || !visited.add(annotationSymbol.type().fullyQualifiedName())) {
        continue;
      }
      if (isSpringBean(annotationSymbol.metadata(), visited)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The project model is built from every file, so files must never be skipped when they are unchanged.
   */
  @Override
  public boolean scanWithoutParsing(InputFileScannerContext inputFileScannerContext) {
    return false;
  }

  private static Set<String> getTypes(Symbol.TypeSymbol symbol) {
    var result = new HashSet<String>();
    collectTypes(symbol.type(), result);
    return result;
  }

  /** Collects the type, all its superclasses and all its direct and inherited interfaces, except {@code java.lang.Object}. */
  private static void collectTypes(@Nullable Type type, Set<String> result) {
    if (type == null || type.isUnknown() || type.is("java.lang.Object") || !result.add(type.fullyQualifiedName())) {
      return;
    }
    Symbol.TypeSymbol typeSymbol = type.symbol();
    collectTypes(typeSymbol.superClass(), result);
    for (Type iface : typeSymbol.interfaces()) {
      collectTypes(iface, result);
    }
  }
}
