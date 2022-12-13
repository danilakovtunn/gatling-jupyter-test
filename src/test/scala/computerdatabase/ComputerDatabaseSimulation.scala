package computerdatabase

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import java.util.concurrent.ThreadLocalRandom
import scala.collection.mutable.ListBuffer


class ComputerDatabaseSimulation extends Simulation {
 
  val headers = Map(
    "Authorization"->"Token bfada895cda3ccf6854f0405ed15117ac0fa8b114537cd40"
  )

  val httpProtocol =
    http.baseUrl("http://localhost:8888")

  val create_kernel = exec(http("Create Kernel")
    .post("http://localhost:8888/api/kernels")
    .headers(headers)
    .check(jsonPath("$.id").saveAs("kernel_id"))
    .check(status is 201)
  )

  val get_ipynb_json = exec(http("Get notebook Cells")
    .get("http://localhost:8888/api/contents/Untitled.ipynb")
    .headers(headers)
    .check(jsonPath("$.content.cells").ofType[Seq[Any]].saveAs("ipynb_json"))
    .check(status is 200)
  )

  val transform_json_to_list = exec(session => {
    var code = new ListBuffer[String]()
    for (cell <- session("ipynb_json").as[Vector[Map[String, String]]] if cell("source").length() > 0) 
      code += cell("source")
    val newSession = session.set("code", code.toList)
    newSession
  })

  val check_criterion = ws.checkTextMessage("Cell Done")
    .matching(jsonPath("$.msg_type").is("execute_reply"))
    .check(jsonPath("$.msg_type").is("execute_reply"))
  
  val delete_kernel = exec(http("Delete Kernel")
    .delete("http://localhost:8888/api/kernels/#{kernel_id}")
    .headers(headers)
    .check(status is 204)
  )

  val printing = exec(session => {
      println("Some useful print:")
      println(session("code").as[String])
      session
    }  
  )

  val connect_ws = exec(ws("Connect WS")
    .connect("ws://localhost:8888/api/kernels/#{kernel_id}/channels")
    .headers(headers)
  )

  val run_single_cell = exec(ws("Run single cell")
    .sendText("""{"header": {"msg_id": "d509fc787aef11eda61129b76ab5a50d", "username": "test", "session": "d509fc797aef11eda61129b76ab5a50d", "data": "2022-12-13T17:10:00.834010", "msg_type": "execute_request", "version": "5.0"}, "parent_header": {"msg_id": "d509fc787aef11eda61129b76ab5a50d", "username": "test", "session": "d509fc797aef11eda61129b76ab5a50d", "data": "2022-12-13T17:10:00.834010", "msg_type": "execute_request", "version": "5.0"}, "metadata": {}, "content": {"code": "#{element}", "silent": false}}""")
    .await(51)(check_criterion)
  )

  val close_connection_ws = exec(ws("Close WS")
    .close  
  )

  val run_all = scenario("User")
    .exec(create_kernel)
    .exec(get_ipynb_json)
    .exec(transform_json_to_list)
    .exec(connect_ws)
    .foreach("#{code}", "element") {
      exec(run_single_cell)
    }
    .exec(close_connection_ws)
    .exec(delete_kernel)

  setUp(
    run_all.inject(rampUsers(4).during(10)),
  ).protocols(httpProtocol)

}
