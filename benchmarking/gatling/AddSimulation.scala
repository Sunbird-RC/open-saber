package opensaber.write // 1

import io.gatling.core.Predef._ // 2
import io.gatling.http.Predef._
import scala.concurrent.duration._

class BasicSimulation extends Simulation { // 3

  val httpConf = http // 4
    .baseURL("http://localhost:8080") // 5
    .header("Content-Type", "application/json")

  val scn = scenario("BasicSimulation") // 7
    .exec(
      http("request_1") // 8
        .post("/add")
        .body(RawFileBody("opensaber/feeders/teacher.jsonld")).asJSON
      )
    .pause(5) // 10

  setUp( // 11
    scn.inject(rampUsers(150) over (10 seconds)) // 12
  ).protocols(httpConf) // 13
}