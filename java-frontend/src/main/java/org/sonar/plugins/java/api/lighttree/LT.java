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

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

public final class LT {

  private LT() {
  }

  public record AssignmentExpr(LightExpr variable, LightExpr expression) implements LightAssignExpr {
  }

  public record BinOp(LightExpr leftOperand, Operator operator, LightExpr rightOperand) implements LightBinOp {
  }

  public record Block(List<? extends LightStat> body) implements LightBlock {
    public Block(LightStat... stats) {
      this(Arrays.stream(stats).toList());
    }
  }

  public record Const(Object rawValue) implements LightLiteral {
    public String value() {
      if (rawValue instanceof String s) {
        return "\"" + s + "\"";
      } else if (rawValue instanceof Character c) {
        return "'" + c + "'";
      } else {
        return rawValue.toString();
      }
    }
  }

  public record Id(String name) implements LightId {
  }

  public record IfStat(LightExpr condition, LightStat thenStatement, @Nullable LightStat elseStatement) implements LightIfStat {
  }

  public record VarDecl(LightTypeNode type, LightId simpleName, LightExpr initializer) implements LightVarDecl {
  }

  public record TypeNode(String typeName) implements LightTypeNode {
  }

  public record Args(List<LightExpr> args) implements LightArguments {
    public Args(LightExpr... args){
      this(Arrays.stream(args).toList());
    }
  }

  public record Invocation(LightExpr methodSelect, LightArguments arguments) implements LightMethodInvocation {
  }

  public record Switch(LightExpr expression, List<LightCaseGroup> cases) implements LightSwitch {
    public Switch(LightExpr expression, LightCaseGroup... cases) {
      this(expression, Arrays.asList(cases));
    }
  }

  public record CaseGroup(List<LightCaseLabel> labels, LightStat bodyAsStat) implements LightCaseGroup {
    public CaseGroup(LightCaseLabel label, LightStat body){
      this(List.of(label), body);
    }
  }

  public record CaseLabel(List<LightExpr> expressions) implements LightCaseLabel {
    public CaseLabel(LightExpr... expressions){
      this(Arrays.asList(expressions));
    }
  }

}
