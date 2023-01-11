package computerdatabase

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.json4s._
import org.json4s.jackson.JsonMethods._


import java.util.concurrent.ThreadLocalRandom
import scala.collection.mutable.ListBuffer


class ComputerDatabaseSimulation extends Simulation {

  def jsonStrToMap(jsonStr: String): Map[String, Any] = {
    implicit val formats = org.json4s.DefaultFormats
    parse(jsonStr).extract[Map[String, Any]]
  } 

  val headers = Map(
    "Authorization"->"Token bfada895cda3ccf6854f0405ed15117ac0fa8b114537cd40"
  )

  val httpProtocol =
    http.baseUrl("http://localhost:8888")

  val create_jupyterlab = exec(http("create jupyterlab")
    .post("http://10.100.203.110:5000/creating")
    .headers(Map("Content-Type" -> "application/json"))
    .body(StringBody("""{"args": {"token": "2bc2c33cb44e56cb9f1e191238ffb78564675fa1", "login": "test4@localhost", "password": "test", "ipynb": {"s3id": "bucket1", "human-name": "my-ipynb-dir1", "mode": "rw"}, "input": [{"s3id": "bucket2", "human-name": "my-input-dataset1", "mode": "r"}], "output": {"s3id": "bucket3", "human-name": "my-output-bucket1","mode": "rw"}}, "system": "jupyterlab", "flavor": "cpu"} """))
    .check(jsonPath("$.sevice_url").saveAs("jupyter_url"))
    .check(jsonPath("$.token").saveAs("token"))
  )
  
  val create_kernel = exec(http("Create Kernel")
    .post("#{jupyter_url}" + "api/kernels")
    .headers(Map("Authorization"->"Token #{token}"))
    .check(jsonPath("$.id").saveAs("kernel_id"))
    .check(status is 201)
  )

  val read_ipynb_local = exec(session => {
    var code = new ListBuffer[String]()
    val source = scala.io.Source.fromFile("/home/danila/ispras/test-jupyter/Untitled.ipynb")
    val lines = try source.mkString finally source.close()

    val cells = jsonStrToMap(lines).asInstanceOf[Map[String, Any]]("cells").asInstanceOf[List[Map[String, List[String]]]]
    for (not_format_cell <- cells if not_format_cell("source").length > 0) {
      var format_cell = ""
      for (cell_string <- not_format_cell("source"))
        format_cell += cell_string
      code += format_cell.replace("\n", "\\n")
    }
    val newSession = session.set("code", code.toList)
    newSession
  })

  val get_ipynb_json = exec(http("Get notebook Cells")
    .get("#{jupyter_url}" + "api/contents/Untitled.ipynb")
    .headers(Map("Authorization"->"Token #{token}"))
    .check(jsonPath("$.content.cells").ofType[Seq[Any]].saveAs("ipynb_json"))
    .check(status is 200)
  )

  val transform_json_to_list = exec(session => {
    var code = new ListBuffer[String]()
    for (cell <- session("ipynb_json").as[Seq[Map[String, String]]] if cell("source").length() > 0)
      code += cell("source").replace("\n", "\\n").replace("\"", "\\\"")
    val newSession = session.set("code", code.toList)
    newSession
  })
  
  val delete_kernel = exec(http("Delete Kernel")
    .delete("#{jupyter_url}" + "api/kernels/#{kernel_id}")
    .headers(Map("Authorization"->"Token #{token}"))
    .check(status is 204)
  )

  val printing = exec(session => {
      println("Some useful print:")
      println(session("element").as[String])
      session
    }  
  )

  val check_criterion = ws.checkTextMessage("Cell Done")
    .matching(jsonPath("$.msg_type").is("execute_reply")
  )

  val connect_ws = exec(ws("Connect WS")
    .connect("ws://localhost:8888/api/kernels/#{kernel_id}/channels")
    .headers(Map("Authorization"->"Token #{token}"))
  )

  val run_single_cell = exec(ws("Run single cell")
    .sendText("""{"header": {"msg_id": "57a169e882c711ed839415fac3fb8477", "username": "test", "session": "57a169e982c711ed839415fac3fb8477", "data": "2022-12-23T16:40:19.867204", "msg_type": "execute_request", "version": "5.0"}, "parent_header": {"msg_id": "57a169e882c711ed839415fac3fb8477", "username": "test", "session": "57a169e982c711ed839415fac3fb8477", "data": "2022-12-23T16:40:19.867204", "msg_type": "execute_request", "version": "5.0"}, "metadata": {}, "content": {"code": "#{element}", "silent": false}}""")
    .await(51)(check_criterion)
  )

  val close_connection_ws = exec(ws("Close WS")
    .close  
  )


  val run_all_from_remote = scenario("User_remote")
    .exec(create_jupyterlab)
    .exec(create_kernel)
    .exec(get_ipynb_json)
    .exec(transform_json_to_list)
    .exec(connect_ws)
    .foreach("#{code}", "element") {
      exec(run_single_cell)
      //.exec(printing)
    }
    .exec(close_connection_ws)
    .exec(delete_kernel)


  val run_all_from_local = scenario("User_local")
    .exec(create_jupyterlab)
    .exec(create_kernel)
    .exec(read_ipynb_local)
    .exec(connect_ws)
    .foreach("#{code}", "element") {
      exec(run_single_cell)
      //.exec(printing)
    }
    .exec(close_connection_ws)
    .exec(delete_kernel)

  setUp(
    run_all_from_local.inject(rampUsers(1).during(10)),
  ).protocols(httpProtocol)

}
