/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.namely.chiefofstate.helper.BaseSpec
import com.typesafe.config.{ Config, ConfigException, ConfigFactory }

class WriteSideConfigSpec extends BaseSpec {
  "Loading write side config" should {
    "be successful when all settings are set" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
            }
          """)
      noException shouldBe thrownBy(WriteSideConfig(config))
    }

    "fail when any of the settings is missing or not properly set" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                use-tls = true
                enable-protos-validation = false
                states-proto = ""
                events-protos = ""
              }
            }
          """)
      an[ConfigException] shouldBe thrownBy(WriteSideConfig(config))
    }
  }
}
