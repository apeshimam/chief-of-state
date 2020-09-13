package com.namely.chiefofstate

import akka.actor.CoordinatedShutdown
import akka.grpc.GrpcClientSettings
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server.{
  LagomApplication,
  LagomApplicationContext,
  LagomApplicationLoader,
  LagomServer
}
import com.namely.chiefofstate.config.{EncryptionSetting, HandlerSetting, ReadSideSetting, SendCommandSettings}
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceClient
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceClient
import com.softwaremill.macwire.wire
import io.superflat.lagompb.{AggregateRoot, BaseApplication, CommandHandler, EventHandler}
import io.superflat.lagompb.encryption.ProtoEncryption
import com.lightbend.lagom.internal.persistence.ReadSideConfig

/**
 * ChiefOfState application
 *
 * @param context the application context
 */
abstract class Application(context: LagomApplicationContext) extends BaseApplication(context) {

  // reflect encryption from config
  override def protoEncryption: Option[ProtoEncryption] = EncryptionSetting(config).encryption

  // making an implicit actor system provider for the generated clients
  implicit lazy val sys = actorSystem

  // wiring up the grpc for the writeSide client
  lazy val writeSideHandlerServiceClient: WriteSideHandlerServiceClient = WriteSideHandlerServiceClient(
    GrpcClientSettings.fromConfig("chief_of_state.v1.WriteSideHandlerService")
  )

  // let us wire up the handler settings
  // this will break the application bootstrapping if the handler settings env variables are not set
  lazy val handlerSetting: HandlerSetting = HandlerSetting(config)

  //  Register a shutdown task to release resources of the client
  coordinatedShutdown
    .addTask(CoordinatedShutdown.PhaseServiceUnbind, "shutdown-writeSidehandler-service-client") { () =>
      writeSideHandlerServiceClient.close()
    }

  // get the SendCommandSettings for the GrpcServiceImpl
  lazy val sendCommandSettings: SendCommandSettings = SendCommandSettings(config)

  // wire up the various event and command handler
  lazy val eventHandler: EventHandler = wire[AggregateEventHandler]
  lazy val commandHandler: CommandHandler = wire[AggregateCommandHandler]

  override lazy val aggregateRoot: AggregateRoot =  wire[Aggregate]

  override lazy val server: LagomServer =
    serverFor[ChiefOfStateService](wire[RestServiceImpl])
      .additionalRouter(wire[GrpcServiceImpl])

  if (config.getBoolean("chief-of-state.read-model.enabled")) {

    // wiring up the grpc for the readSide client
    ReadSideSetting.getReadSideSettings.foreach { config =>
      lazy val readSideHandlerServiceClient: ReadSideHandlerServiceClient =
        ReadSideHandlerServiceClient(config.getGrpcClientSettings(actorSystem))

      coordinatedShutdown.addTask(
        CoordinatedShutdown.PhaseServiceUnbind,
        s"shutdown-readSidehandler-service-client-${config.processorId}"
      ) { () =>
        readSideHandlerServiceClient.close()
      }

      lazy val chiefOfStateReadProcessor: ReadSideHandler = wire[ReadSideHandler]
      chiefOfStateReadProcessor.init()
    }
  }
}

/**
 * ApplicationLoader boostraps the application at runtime
 */
class ApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new Application(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new Application(context) with LagomDevModeComponents

  override def describeService: Option[Descriptor] = Some(readDescriptor[ChiefOfStateService])
}
