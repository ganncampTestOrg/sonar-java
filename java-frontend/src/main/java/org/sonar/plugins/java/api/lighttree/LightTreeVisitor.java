/*
 * SonarQube Java
 * Copyright (C) 2012-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.plugins.java.api.lighttree;

public interface LightTreeVisitor {

  void visitLightAssignExpr(LightAssignExpr assignExpr);

  void visitLightBinOp(LightBinOp binop);

  void visitLightBlock(LightBlock block);

  void visitLightId(LightId id);

  void visitLightIfStat(LightIfStat ifStat);

  void visitLightLiteral(LightLiteral literal);

  void visitLightTypeNode(LightTypeNode typeNode);

  void visitLightVarDecl(LightVarDecl varDecl);

  void visitLightMethodInvocation(LightMethodInvocation invocation);

  void visitLightArguments(LightArguments args);

  void visitLightSwitch(LightSwitch swtch);

  void visitLightCaseGroup(LightCaseGroup caseGroup);

  void visitLightCaseLabel(LightCaseLabel label);

}
