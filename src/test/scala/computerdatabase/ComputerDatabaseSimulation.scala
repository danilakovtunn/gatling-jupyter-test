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


  val printing = exec(session => {
      println("usage filename: :")
      println(session("filename").as[String])
      println(session("all").as[String])
      session
    }  
  )

  val httpProtocol =
    http.baseUrl("http://localhost:8888")
  
  val feeder = csv("request.csv").random

  val create_jupyterlab = exec(
    feed(feeder)
    .exec(http("create jupyterlab")
      .post("http://10.100.203.110:5000/creating")
      .headers(Map("Content-Type" -> "application/json"))
      .body(RawFileBody("#{filename}"))
      .check(jsonPath("$.service_url").saveAs("jupyter_url"))
      .check(jsonPath("$.token").saveAs("token"))
      .check(bodyString.saveAs("all"))
    )
  )
  
  val create_kernel = exec(http("Create Kernel")
    .post("#{jupyter_url}" + "api/kernels")
    .requestTimeout(180)
    .headers(Map("Authorization"->"Token #{token}"))
    .check(jsonPath("$.id").saveAs("kernel_id"))
    .check(status is 201)
  )

  val read_ipynb_local = exec(session => {
    var code = new ListBuffer[String]()
    val source = scala.io.Source.fromFile("/home/danila/diplom/automatic_user/test/Untitled.ipynb")
    val lines = try source.mkString finally source.close()

    val cells = jsonStrToMap(lines).asInstanceOf[Map[String, Any]]("cells").asInstanceOf[List[Map[String, List[String]]]]
    for (not_format_cell <- cells if not_format_cell("source").length > 0) {
      var format_cell = ""
      for (cell_string <- not_format_cell("source"))
        format_cell += cell_string
      code += format_cell.replace("\n", "\\n").replace("\"", "\\\"")
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

  val check_criterion = ws.checkTextMessage("Cell Done")
    .matching(jsonPath("$.msg_type").is("execute_reply")
  )

  val connect_ws = exec(ws("Connect WS")
    .connect(session => "ws" + session("jupyter_url").as[String].substring(4) + "api/kernels/" + session("kernel_id").as[String] + "/channels")
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
    }
    .exec(close_connection_ws)
    .exec(delete_kernel)


  val run_all_from_local = scenario("User_local")
    .exec(create_jupyterlab)
    .exec(printing)
    .exec(create_kernel)
    .exec(read_ipynb_local)
    .exec(connect_ws)
    .foreach("#{code}", "element") {
      exec(run_single_cell)
    }
    .exec(close_connection_ws)
    .exec(delete_kernel)

  setUp(
    //run_all_from_local.inject(rampUsers(10).during(30)),
    run_all_from_local.inject(atOnceUsers(10)),
  ).protocols(httpProtocol)

}
