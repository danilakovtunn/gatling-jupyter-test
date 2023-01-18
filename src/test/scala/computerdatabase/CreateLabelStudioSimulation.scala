import io.gatling.core.Predef._
import io.gatling.http.Predef._

class CreateLabelStudioSimulation extends Simulation {

  val printing = exec(session => {
      println("usage filename:")
      println(session("label_studio").as[String])
      println(session("all").as[String])
      session
    }  
  )

  val httpProtocol =
    http.baseUrl("http://localhost:8888")
  
  val feeder = csv("request.csv").shuffle

  val create_jupyterlab = exec(
    feed(feeder)
    .exec(http("create label studio")
      .post(System.getProperty("protocol") + "://" + System.getProperty("jaas_url") + ":" + System.getProperty("port") + "/creating")
      .headers(Map("Content-Type" -> "application/json"))
      .body(RawFileBody("#{label_studio}"))
      .check(jsonPath("$.service_url").saveAs("label_studio_url"))
      .check(bodyString.saveAs("all"))
    )
  )

  val run_all_from_local = scenario("test label_studio as a service")
    .exec(create_jupyterlab)
    .exec(printing)


  setUp(
    run_all_from_local.inject(rampUsers(Integer.getInteger("users", 1)).during(Integer.getInteger("ramp", 0)))
  ).protocols(httpProtocol)

}
