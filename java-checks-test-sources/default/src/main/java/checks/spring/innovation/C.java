package checks.spring.innovation;

import org.springframework.stereotype.Service;

@Service
public class C implements Named {
  @Override
  public String getName() {
    return "C";
  }
}
