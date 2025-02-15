/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.testcontainers.utility.DockerImageName

class ReadSideBootstrapSpec extends BaseSpec with ForAllTestContainer {

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(dockerImageName = DockerImageName.parse("postgres:11"), urlParams = Map("currentSchema" -> cosSchema))
    .createContainer()

  lazy val config: Config = ConfigFactory
    .parseResources("test.conf")
    .withValue("jdbc-default.url", ConfigValueFactory.fromAnyRef(container.jdbcUrl))
    .withValue("jdbc-default.user", ConfigValueFactory.fromAnyRef(container.username))
    .withValue("jdbc-default.password", ConfigValueFactory.fromAnyRef(container.password))
    .withValue("jdbc-default.hikari-settings.max-pool-size", ConfigValueFactory.fromAnyRef(1))
    .withValue("jdbc-default.hikari-settings.min-idle-connections", ConfigValueFactory.fromAnyRef(1))
    .withValue("jdbc-default.hikari-settings.idle-timeout-ms", ConfigValueFactory.fromAnyRef(1000))
    .withValue("jdbc-default.hikari-settings.max-lifetime-ms", ConfigValueFactory.fromAnyRef(3000))
    .withValue("chief-of-state.read-side.enabled", ConfigValueFactory.fromAnyRef(true))
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  lazy val actorSystem = testKit.system

  override def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  ".apply" should {
    "construct without failure" in {
      noException shouldBe thrownBy(ReadSideBootstrap(actorSystem, Seq(), 2))
    }
  }
  ".getDataSource" should {
    "return a hikari data source" in {
      val dbConfig = ReadSideBootstrap.DbConfig(
        jdbcUrl = container.jdbcUrl,
        username = container.username,
        password = container.password,
        maxPoolSize = 1,
        minIdleConnections = 1,
        idleTimeoutMs = 1000,
        maxLifetimeMs = 3000)
      val dataSource = ReadSideBootstrap.getDataSource(dbConfig)

      noException shouldBe thrownBy(dataSource.getConnection().close())
    }
  }
}
