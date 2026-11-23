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
package org.sonar.plugins.java.api.internal;

import org.sonar.java.annotations.Beta;

/**
 * Marker for checks that read the project context model at the end of the analysis.
 * Building the model requires every main file to be parsed, so the model is only built when at least one active check implements this interface.
 * This keeps incremental analysis (skipping unchanged files) working for projects that do not use such checks.
 */
@Beta
public interface ProjectContextModelConsumer {
}
