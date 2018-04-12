package com.raphtory

import java.net.InetAddress

import ch.qos.logback.classic.Level
import com.raphtory.caseclass.clustercase._
import com.raphtory.caseclass.clustercase.{ManagerNode, RestNode, SeedNode, WatchDogNode}
import com.typesafe.config.ConfigFactory
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.slf4j.LoggerFactory
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import kamon.system.SystemMetrics
import java.lang.management.ManagementFactory

//main function
object Go extends App {
  val conf          = ConfigFactory.load()
  val seedLoc       = s"${sys.env("HOST_IP")}:${conf.getInt("settings.bport")}"
  val zookeeper     = s"${sys.env("ZOOKEEPER")}"


  val runtimeMxBean = ManagementFactory.getRuntimeMXBean
  val arguments = runtimeMxBean.getInputArguments
  println(s"Current java options: ${arguments}")
  args(0) match {
    case "seedNode" => {
      println("Creating seed node")
      setConf(seedLoc, zookeeper)
      SeedNode(seedLoc)
    }
    case "rest" => {
      println("Creating rest node")
      RestNode(getConf(zookeeper))
    }
    case "router" => {
      println("Creating Router")
      RouterNode(getConf(zookeeper))
    }
    case "partitionManager" => {
      println(s"Creating Patition Manager...")
      ManagerNode(getConf(zookeeper))
    }

    case "updater" => {
      println("Creating Update Generator")
      UpdateNode(getConf(zookeeper))
    }

    case "LiveAnalysisManager" => {
      println("Creating Live Analysis Manager")
      val LAM_Name = args(1) // TODO other ways (still env?): see issue #5 #6
      LiveAnalysisNode(getConf(zookeeper), LAM_Name)
    }
    case "clusterUp" => {
      println("Cluster Up, informing Partition Managers and Routers")
      WatchDogNode(getConf(zookeeper), sys.env("PARTITION_MIN").toInt, sys.env("ROUTER_MIN").toInt)
    }

  }

  def setConf(seedLoc: String, zookeeper: String): Unit = {
    val retryPolicy = new ExponentialBackoffRetry(1000, 3)
    val curatorZookeeperClient =
      CuratorFrameworkFactory.newClient(zookeeper, retryPolicy)

    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[ch.qos.logback.classic.Logger]
    root.setLevel(Level.ERROR)


    curatorZookeeperClient.start
    curatorZookeeperClient.getZookeeperClient.blockUntilConnectedOrTimedOut
    if (curatorZookeeperClient.checkExists().forPath("/seednode") == null) {
      curatorZookeeperClient
        .create()
        .creatingParentsIfNeeded()
        .forPath("/seednode", seedLoc.getBytes)
    } else {
      curatorZookeeperClient.setData().forPath("/seednode", seedLoc.getBytes)
    }
    val originalData = new String(
      curatorZookeeperClient.getData.forPath("/seednode"))
    curatorZookeeperClient.close()
  }

  def getConf(zookeeper: String): String = {
    //Change
    val retryPolicy = new ExponentialBackoffRetry(1000, 3)
    val curatorZookeeperClient =
      CuratorFrameworkFactory.newClient(zookeeper, retryPolicy)

    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[ch.qos.logback.classic.Logger]
    root.setLevel(Level.ERROR)

    curatorZookeeperClient.start
    curatorZookeeperClient.getZookeeperClient.blockUntilConnectedOrTimedOut
    val originalData = new String(
      curatorZookeeperClient.getData.forPath("/seednode"))
    println(originalData)
    prometheusReporter()
    curatorZookeeperClient.close()
    hostname2Ip(originalData)
  }
  //https://blog.knoldus.com/2014/08/29/how-to-setup-and-use-zookeeper-in-scala-using-apache-curator/

  def prometheusReporter() = {
    SystemMetrics.startCollecting()
    Kamon.addReporter(new PrometheusReporter())
  }

  def hostname2Ip(seedLoc: String): String = {
    // hostname_asd_1:port
    val t = seedLoc.split(":")
    return InetAddress.getByName(t(0)).getHostAddress() + ":" + t(1)
  }

}
