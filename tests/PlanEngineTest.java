import app.rwun.PlanEngine;
import java.time.LocalDate;
import java.util.HashSet;

public class PlanEngineTest {
  public static void main(String[] args) {
    PlanEngine engine = new PlanEngine();
    for (int days = 2; days <= 5; days++)
      for (int weeks = 4; weeks <= 24; weeks++) {
        var config = new PlanEngine.Config(weeks, days, 20, 10, LocalDate.of(2026, 10, 19));
        var plan = engine.generate(config);
        check(plan.size() == weeks * days, "session count");
        var dates = new HashSet<LocalDate>();
        var ids = new HashSet<String>();
        for (var session : plan) {
          check(dates.add(session.date()), "duplicate workout date");
          check(ids.add(session.id()), "duplicate identifier");
          check(session.km() > 0 && Double.isFinite(session.km()), "invalid distance");
          check(!session.date().isBefore(config.start()), "date before start");
          check(session.date().isBefore(config.start().plusWeeks(weeks)), "date beyond plan");
        }
        check(plan.equals(engine.generate(config)), "nondeterministic plan");
      }
    expectInvalid(() -> new PlanEngine.Config(3, 3, 12, 5, LocalDate.now()));
    expectInvalid(() -> new PlanEngine.Config(8, 6, 12, 5, LocalDate.now()));
    expectInvalid(() -> new PlanEngine.Config(8, 3, Double.NaN, 5, LocalDate.now()));
    expectInvalid(() -> new PlanEngine.Config(8, 3, 12, Double.POSITIVE_INFINITY, LocalDate.now()));
    System.out.println("Plan invariants passed across 84 configurations.");
  }

  static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static void expectInvalid(Runnable run) {
    try {
      run.run();
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new AssertionError("invalid configuration accepted");
  }
}
