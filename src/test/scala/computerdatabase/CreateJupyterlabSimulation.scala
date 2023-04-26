import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.json4s._
import org.json4s.jackson.JsonMethods._


import java.util.concurrent.ThreadLocalRandom
import scala.collection.mutable.ListBuffer


class CreateJupyterlabSimulation extends Simulation {

  def jsonStrToMap(jsonStr: String): Map[String, Any] = {
    implicit val formats = org.json4s.DefaultFormats
    parse(jsonStr).extract[Map[String, Any]]
  } 


  val printing = exec(session => {
      println("usage s3id: ")
      println(session("s3id").as[String])
      println(session("all").as[String])
      //println(session("element").as[String])
      session
    }  
  )

  val httpProtocol =
    http.baseUrl("http://localhost:8888")
  
  val feeder_jupyterlab = csv("jupyterlab.csv").random

  val create_jupyterlab = exec(
    feed(feeder_jupyterlab)
    .exec(http("create jupyterlab")
      .post(System.getProperty("protocol") + "://" + System.getProperty("jaas_url") + ":" + System.getProperty("port") + "/creating")
      .headers(Map("Content-Type" -> "application/json"))
      .body(ElFileBody("create_jupyterlab.json"))
      .check(jsonPath("$.service_url").saveAs("jupyter_url"))
      .check(jsonPath("$.token").saveAs("token"))
      .check(bodyString.saveAs("all"))
    )
  )
  
  val create_kernel = exec(http("Create Kernel")
    .post("#{jupyter_url}" + "api/kernels")
    .headers(Map("Authorization"->"Token #{token}"))
    .check(jsonPath("$.id").saveAs("kernel_id"))
    .check(status is 201)
  )

  val create_kernel1 = exec(http("Create Kernel")
    .post("#{jupyter_url}" + "api/kernels")
    .headers(Map("Authorization"->"Token #{token}"))
    .check(jsonPath("$.id").saveAs("kernel_id1"))
    .check(status is 201)
  )

  val feeder_notebook = csv("notebooks.csv").random

  val read_ipynb_local = exec(
    feed(feeder_notebook)
      .exec(session => {
      var code1 = new ListBuffer[String]()
      val source = scala.io.Source.fromFile("src/test/resources/notebooks/" + session("notebook").as[String])
      val lines = try source.mkString finally source.close()

      val cells = jsonStrToMap(lines).asInstanceOf[Map[String, Any]]("cells").asInstanceOf[List[Map[String, List[String]]]]
      for (not_format_cell <- cells if not_format_cell("source").length > 0 && not_format_cell("cell_type") == "code") {
        var format_cell1 = ""
        for (cell_string <- not_format_cell("source"))
          format_cell1 += cell_string
        code1 += format_cell1.replace("\n", "\\n").replace("\"", "\\\"")
      } 
      //code1 = code1.slice(0, 24)
      val newSession = session.set("code1", code1.toList)
      newSession
    })
  )

  val read_ipynb_local_requiers = exec(session => {
    var code = new ListBuffer[String]()
    val source = scala.io.Source.fromFile("src/test/resources/notebooks/requiers.ipynb")
    val lines = try source.mkString finally source.close()

    val cells = jsonStrToMap(lines).asInstanceOf[Map[String, Any]]("cells").asInstanceOf[List[Map[String, List[String]]]]
    for (not_format_cell <- cells if not_format_cell("source").length > 0 && not_format_cell("cell_type") == "code") {
      var format_cell = ""
      for (cell_string <- not_format_cell("source"))
        format_cell += cell_string
      code += format_cell.replace("\n", "\\n").replace("\"", "\\\"")
    } 
    //code = code.slice(0, 24)
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

  val delete_kernel1 = exec(http("Delete Kernel")
    .delete("#{jupyter_url}" + "api/kernels/#{kernel_id1}")
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

  val connect_ws1 = exec(ws("Connect WS")
    .connect(session => "ws" + session("jupyter_url").as[String].substring(4) + "api/kernels/" + session("kernel_id1").as[String] + "/channels")
    .headers(Map("Authorization"->"Token #{token}"))
  )

  val run_single_cell = exec(ws("Run single cell")
    .sendText(ElFileBody("ws_text.json"))
    .await(1000)(check_criterion)
  )

  val run_single_cell1 = exec(ws("Run single cell")
    .sendText(ElFileBody("ws_text1.json"))
    .await(1000)(check_criterion)
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
    .exec(read_ipynb_local_requiers)
    .exec(connect_ws)
    .foreach("#{code}", "element") {
      exec(run_single_cell)
      //.exec(printing)
    }
    .exec(close_connection_ws)
    .exec(delete_kernel)
    .exec(create_kernel1)
    .exec(read_ipynb_local)
    .exec(connect_ws1)
    .foreach("#{code1}", "element1") {
      exec(run_single_cell1)
      //.exec(printing)
    }
    .exec(close_connection_ws)
    .exec(delete_kernel1)

  setUp(
    run_all_from_local.inject(rampUsers(Integer.getInteger("users", 5)).during(Integer.getInteger("ramp", 0)))
  ).protocols(httpProtocol)

}
