import static java.lang.ScopedValue.where;


static final ScopedValue<String> SCOPED = ScopedValue.newInstance();
static final ScopedValue<String> SCOPED2 = ScopedValue.newInstance();

// Field storage - should be flagged as we can't track field usage reliably
ScopedValue.Carrier carrierField;
static ScopedValue.Carrier staticCarrierField;


void chainedWhereBeforeConsumption() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - chained .where() then .run()
  carrier.where(SCOPED2, "world").run(() -> {
  });
}

void chainedWhereBeforeCallConsumption() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - chained .where() then .call()
  carrier.where(SCOPED2, "world").call(() -> "result");
}

// ===== CONTROL FLOW =====

void conditionalUsage(boolean condition) {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - used conditionally
  if (condition) {
    carrier.run(() -> {
    });
  }
}

void conditionalUsageNotAllPaths(boolean condition) {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - used in one branch (we don't do path-sensitive analysis)
  if (condition) {
    carrier.run(() -> {
    });
  }
  // else: carrier not used - but we can't detect this without CFG analysis
}

void carrierInTryFinally() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - used in finally
  try {
    doSomething();
  } finally {
    carrier.run(() -> {
    });
  }
}

void carrierInLoop() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - used in loop
  for (int i = 0; i < 3; i++) {
    carrier.run(() -> {
    });
  }
}

// ===== VARIABLE SCENARIOS =====

void variableReassignedBeforeUse() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Noncompliant
  carrier = ScopedValue.where(SCOPED, "world");
  carrier.run(() -> {
  });
}

void multipleVariablesSameCarrierUsed() {
  var carrier1 = ScopedValue.where(SCOPED, "hello"); // Compliant - used via carrier2 reference... actually no, different objects
  var carrier2 = carrier1; // Carrier2 points to same object
  carrier2.run(() -> {
  });
}


void multipleVariablesSameCarrierUnused() {
  var carrier1 = ScopedValue.where(SCOPED, "hello"); // Noncompliant
  var carrier2 = carrier1; // Carrier2 points to same object but is never used
}

void multipleVariablesOneUnused() {
  var usedCarrier = ScopedValue.where(SCOPED, "hello"); // Compliant
  var unusedCarrier = ScopedValue.where(SCOPED, "world"); // Noncompliant
  usedCarrier.run(() -> {
  });
}

void carrierStoredInField() {
  carrierField = ScopedValue.where(SCOPED, "hello"); // Compliant - way of escaping
}

void carrierStoredInStaticField() {
  staticCarrierField = ScopedValue.where(SCOPED, "hello"); // Compliant - way of escaping
}

// ===== RETURN VARIATIONS =====

ScopedValue.Carrier directReturnWithoutVariable() {
  return ScopedValue.where(SCOPED, "hello"); // Compliant - returned directly
}

ScopedValue.Carrier ternaryReturn(boolean condition) {
  var carrier1 = ScopedValue.where(SCOPED, "hello"); // Compliant - potentially returned
  var carrier2 = ScopedValue.where(SCOPED, "world"); // Compliant - potentially returned
  return condition ? carrier1 : carrier2;
}

// ===== NON-CONSUMING METHOD CALLS =====

void toStringOnCarrier() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Noncompliant
  carrier.toString();
}

void hashCodeOnCarrier() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Noncompliant
  carrier.hashCode();
}

void equalsOnCarrier() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Noncompliant
  carrier.equals(null);
}

void getOnCarrier() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Noncompliant
  carrier.get(SCOPED);
}

// ===== LAMBDA/CLOSURE =====

void carrierUsedInsideLambda() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - used inside lambda
  Runnable r = () -> {
    carrier.run(() -> {
    });
  };
  r.run();
}

void carrierPassedToLambdaConsumer() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - escapes to lambda consumer
  consumeCarrier(c -> c.run(() -> {
  }), carrier);
}

// ===== NESTED ESCAPING =====

void carrierPassedToMethodThatReturnsIt() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - escapes via method call
  var returned = identity(carrier);
  returned.run(() -> {
  });
}

void carrierPassedToMethodThatReturnsItButUnused() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - escapes via method call (we can't track further)
  var returned = identity(carrier);
  // returned is not used - but carrier escaped so we don't flag
}

// ===== STATIC IMPORT =====

void staticImportWhere() {
  where(SCOPED, "hello"); // Noncompliant
}

void staticImportWhereUsed() {
  where(SCOPED, "hello").run(() -> {
  }); // Compliant - used immediately
}

void staticImportWhereVariable() {
  var carrier = where(SCOPED, "hello"); // Noncompliant
}

void staticImportWhereVariableUsed() {
  var carrier = where(SCOPED, "hello"); // Compliant - used
  carrier.run(() -> {
  });
}

// ===== CONSTRUCTOR ARGUMENT =====

void carrierAsConstructorArgument() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - escapes via constructor
  new CarrierHolder(carrier);
}

void carrierAsConstructorArgumentDirect() {
  new CarrierHolder(ScopedValue.where(SCOPED, "hello")); // Compliant - escapes via constructor
}

// ===== REASSIGNMENT, BUILDER PATTERN =====

void builderPatternReassignment() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - the carrier is extended and run
  carrier = carrier.where(SCOPED2, "world");
  carrier.run(() -> {
  });
}

void builderPatternInLoop(java.util.List<String> values) {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - extended in a loop and run
  for (String value : values) {
    carrier = carrier.where(SCOPED2, value);
  }
  carrier.run(() -> {
  });
}

void conditionalReassignment(boolean condition) {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - the first carrier is used when the condition is false
  if (condition) {
    carrier = ScopedValue.where(SCOPED2, "world");
  }
  carrier.run(() -> {
  });
}

void reassignedAfterUse() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - used before the reassignment
  carrier.run(() -> {
  });
  carrier = ScopedValue.where(SCOPED, "world");
  carrier.run(() -> {
  });
}

// ===== PARAMETERS =====

interface CarrierConsumer {
  void accept(ScopedValue.Carrier carrier); // Compliant - no body
}

abstract static class AbstractCarrierConsumer {
  abstract void accept(ScopedValue.Carrier carrier); // Compliant - no body
}

static class StoringCarrierHolder {
  ScopedValue.Carrier stored;

  StoringCarrierHolder(ScopedValue.Carrier carrier) { // Compliant - stored in a field
    this.stored = carrier;
  }

  void set(ScopedValue.Carrier carrier) { // Compliant - stored in a field
    stored = carrier;
  }
}

// ===== FIELDS WITH where() INITIALIZER =====

static final ScopedValue.Carrier CONTEXT = ScopedValue.where(SCOPED, "hello"); // Compliant - fields can be used from anywhere

void fieldUsedWithQualifiedName() {
  EdgeCasesHolder.CONTEXT.run(() -> {
  });
}

static class EdgeCasesHolder {
  static final ScopedValue.Carrier CONTEXT = ScopedValue.where(SCOPED, "hello"); // Compliant - used with a qualified name
}

// ===== TERNARY, PARENTHESES, CAST =====

void ternaryAssignedToVariable(boolean condition) {
  var carrier = condition ? ScopedValue.where(SCOPED, "a") : ScopedValue.where(SCOPED, "b"); // Compliant - run below
  carrier.run(() -> {
  });
}

void ternaryAssignedToUnusedVariable(boolean condition) {
  var carrier = condition ? ScopedValue.where(SCOPED, "a") : ScopedValue.where(SCOPED, "b"); // Noncompliant
}

void ternaryUsedDirectly(boolean condition) {
  (condition ? ScopedValue.where(SCOPED, "a") : ScopedValue.where(SCOPED, "b")).run(() -> {
  });
}

void parenthesizedUsedDirectly() {
  (ScopedValue.where(SCOPED, "hello")).run(() -> {
  });
}

void parenthesizedUnusedVariable() {
  var carrier = (ScopedValue.where(SCOPED, "hello")); // Noncompliant
}

void parenthesizedVariableUse() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant
  (carrier).run(() -> {
  });
}

ScopedValue.Carrier castReturned() {
  return (ScopedValue.Carrier) ScopedValue.where(SCOPED, "hello"); // Compliant - returned
}

void ternaryPickedThenRun(boolean flag) {
  var c1 = ScopedValue.where(SCOPED, "a"); // Compliant - picked by the ternary and run
  var c2 = ScopedValue.where(SCOPED, "b"); // Compliant - picked by the ternary and run
  (flag ? c1 : c2).run(() -> {
  });
}

void ternaryPickedIntoVariableThenRun(boolean flag) {
  var c1 = ScopedValue.where(SCOPED, "a"); // Compliant - picked by the ternary and run
  var c2 = ScopedValue.where(SCOPED, "b"); // Compliant - picked by the ternary and run
  var c = flag ? c1 : c2;
  c.run(() -> {
  });
}

void parenthesizedChainedWhereThenRun() {
  var c = ScopedValue.where(SCOPED, "a"); // Compliant - extended and run
  (c.where(SCOPED2, "b")).run(() -> {
  });
}

// ===== OTHER ESCAPING FORMS =====

java.util.function.Supplier<ScopedValue.Carrier> lambdaBodyReturnsCarrier() {
  return () -> ScopedValue.where(SCOPED, "hello"); // Compliant - returned by the lambda
}

void storedInFieldThroughVariable() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - stored in a field
  this.carrierField = carrier;
}

void storedInFieldWithoutThis() {
  var carrier = ScopedValue.where(SCOPED, "hello"); // Compliant - stored in a field
  carrierField = carrier;
}

ScopedValue.Carrier yielded(int i) {
  return switch (i) {
    case 0 -> ScopedValue.where(SCOPED, "a"); // Compliant - yielded
    default -> {
      yield ScopedValue.where(SCOPED, "b"); // Compliant - yielded
    }
  };
}

ScopedValue.Carrier[] storedInArray() {
  return new ScopedValue.Carrier[] {ScopedValue.where(SCOPED, "hello")}; // Compliant - escapes in an array
}

// ===== HELPER METHODS AND CLASSES =====

void doSomething() {
}

ScopedValue.Carrier identity(ScopedValue.Carrier carrier) {
  return carrier;
}

void consumeCarrier(java.util.function.Consumer<ScopedValue.Carrier> consumer, ScopedValue.Carrier carrier) {
  consumer.accept(carrier);
}

static class CarrierHolder {
  CarrierHolder(ScopedValue.Carrier carrier) // Noncompliant
  {
  }
}
