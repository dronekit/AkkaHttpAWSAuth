import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, HttpMethods, HttpRequest}
import akka.stream.scaladsl.{Source, Sink}
import cloud.drdrdr.utils.AWSCredentials.AWSPermissions
import org.scalatest.{Matchers, FunSpec}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import cloud.drdrdr.SignRequestForAWS
import cloud.drdrdr.utils.AWSCredentials
import cloud.drdrdr.utils.Config.awsConfig
import org.scalatest.{FunSpec, Matchers}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Future, Await, ExecutionContext}

/**
 * Created by Adam Villaflor on 1/22/2016.
  *
 */
class EC2CredentialsSpec extends FunSpec with Matchers with SignRequestForAWS{
  implicit val testSystem = akka.actor.ActorSystem("test-system")
  implicit val ec: ExecutionContext = testSystem.dispatcher
  implicit val materializer = ActorMaterializer()

  private def jsonPrint(response: HttpResponse) {
    val responseData =  Await.result(response.entity.dataBytes.map(_.utf8String).grouped(Int.MaxValue).runWith(Sink.head), 10 seconds).mkString
    val responseJson = responseData.parseJson
    println(responseJson.prettyPrint)
  }

  //sends outgoing request
  private def post(httpRequest: HttpRequest): Future[HttpResponse] = {
    val endpoint = httpRequest.uri.toString()
    val uri = java.net.URI.create(endpoint)
    val outgoingConn = if (uri.getScheme == "https") {
      Http().outgoingConnectionHttps(uri.getHost, if (uri.getPort == -1) 443 else uri.getPort)
    } else {
      Http().outgoingConnection(uri.getHost, if (uri.getPort == -1) 80 else uri.getPort)
    }
    Source.single(httpRequest).via(outgoingConn).runWith(Sink.head)
  }

  describe("Should") {
    it ("get credentials") {
      val credentialSource = AWSCredentials.getAmazonEC2CredentialsSource()
      val credentials = Await.result(credentialSource.getCredentials, 10 seconds)
      credentials.token.isEmpty shouldBe false
    }
    it ("send a request") {
      val credentialsSource = AWSCredentials.getAmazonEC2CredentialsSource()
      Await.result(credentialsSource.getCredentials, 10 seconds) match {
        case permission =>
//          val accessKeyID = permission.accessKeyId
//          val kSecret = permission.secretAccessKey
//          println(accessKeyID)
//          val token = permission.token
          val baseURI = awsConfig.getString("URI")
          val service = awsConfig.getString("service")
          val region = awsConfig.getString("region")
          val URI = s"${baseURI}?Version=2013-10-15&Action=DescribeRegions"
          val request = HttpRequest(
            method = HttpMethods.GET,
            uri = URI
          )
          val authRequest = Await.result(addAuthorizationHeaderFromCredentialsSource(request, region, service, credentialsSource), 15 seconds)
          println(authRequest)
          val response = Await.result(post(authRequest), 10 seconds)
          jsonPrint(response)
          response.status shouldBe StatusCodes.OK
      }
    }
    it ("send a request using general get method") {
      val credentialsSource = AWSCredentials.getCredentials(profile = "fail")
      Await.result(credentialsSource.getCredentials, 10 seconds) match {
        case permission:AWSPermissions =>
          val accessKeyID = permission.accessKeyId
          println(accessKeyID)
          val kSecret = permission.secretAccessKey
          println(kSecret)
          val token = permission.token
          val baseURI = awsConfig.getString("URI")
          val service = awsConfig.getString("service")
          val region = awsConfig.getString("region")
          val URI = s"${baseURI}?Version=2013-10-15&Action=DescribeRegions"
          val request = HttpRequest(
            method = HttpMethods.GET,
            uri = URI
          )
          val authRequest = Await.result(addAuthorizationHeader(request, kSecret, region, accessKeyID, service, token), 15 seconds)
          println(authRequest)
          val response = Await.result(post(authRequest), 10 seconds)
          jsonPrint(response)
          response.status shouldBe StatusCodes.OK
      }
    }
  }
}
