package checks.spring.innovation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class DemoApplication implements CommandLineRunner {

  @Autowired
  Named nameSource; // Noncompliant

  @Autowired
  A a;

  @Autowired
  Named second; // Noncompliant

  @Autowired
  Base base; // Noncompliant

  @Autowired
  Environment environment; // Compliant: no @Component implementation, must not fail

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    System.out.println("Hello,  " + nameSource.getName() + "!");
  }
}
