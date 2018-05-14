package tableMigration

import aws.AwsContextProvider
import aws.dynamo.{Dynamo, DynamoTemplateId}
import com.amazonaws.regions.Regions
import com.gu.scanamo.{Scanamo, Table}
import models._
import aws.dynamo.DynamoFormats._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.gu.scanamo.error.DynamoReadError
import com.ovoenergy.comms.model.TemplateManifest
import com.ovoenergy.comms.templates.util.Hash
import cats.implicits._
import models.Brand._

import scala.collection.immutable

object TableMigrator extends App {

  val (dynamoClient, s3Client) = AwsContextProvider.genContext(false, Regions.EU_WEST_1)

  val dynamo = new Dynamo(
    dynamoClient,
    Table[TemplateVersionLegacy]("template-manager-DEV-TemplateVersionTable-15FUF2VRQBG72"),
    Table[TemplateSummaryLegacy]("template-manager-DEV-TemplateSummaryTable-1EQQIGC8NAPIC")
  )

  val newTemplateSummary = Table[TemplateSummary]("template-manager-DEV-TemplateSummaryTIDTable-KGQCA9FT8ZG3")
  val newTemplateVersion = Table[TemplateVersion]("template-manager-DEV-TemplateVersionTIDTable-OZ6W3G3K0WMS")

  val dynamoTemplateId = new DynamoTemplateId(
    dynamoClient,
    newTemplateVersion,
    newTemplateSummary
  )

  def createNewTemplateSummariesTable() = dynamo
    .listTemplateSummaries
    .map(legacy => TemplateSummary(Hash(legacy.commName), legacy.commName, brands(legacy.commName), legacy.commType, legacy.latestVersion))
    .map{nts =>
      saveTemplateSummary(nts)
      nts
    }
    .map(a => createNewTemplateVersionTable(a.commName))

  def createNewTemplateVersionTable(commName: String) = dynamo
    .listVersions(commName)
    .map(legacy => TemplateVersion(Hash(legacy.commName), legacy.version, legacy.commName, legacy.commType, legacy.publishedAt, legacy.publishedBy, legacy.channels))
    .map(saveTemplateVersion)

  def saveTemplateSummary(templateSummary: TemplateSummary) = Scanamo.exec(dynamoClient)(newTemplateSummary.put(templateSummary))
  def saveTemplateVersion(templateVersion: TemplateVersion) = Scanamo.exec(dynamoClient)(newTemplateVersion.put(templateVersion))

  val brands = Map(
    "props-green-electricity-bolton-confirmation-with-dd" ->  Ovo,
    "props-green-bolton-cancellation" ->  Ovo,
    "payg-credit-transfer-single" ->  Boost,
    "PAYGSmartMeterAvailabilityComm" ->  Boost,
    "PAYG_ONBOARDING_SSD_AMENDED_DUAL_ELECTRICITY" ->  Boost,
    "PAYG_ONBOARDING_SUPPLY_START_DUAL_GAS_FIRST" ->  Boost,
    "SmartMeterBookingConfirmation" ->  Ovo,
    "PAYG_ONBOARDING_SSD_KNOWN_ELECTRICITY" ->  Boost,
    "orion-email-changed-new" ->  Ovo,
    "PAYG_ONBOARDING_SSD_AMENDED_DUAL_DIFFERENT" ->  Boost,
    "boost-sms-test" ->  Boost,
    "PAYG_ONBOARDING_SUPPLY_START_DUAL_GAS_LAST" ->  Boost,
    "PAYG_ONBOARDING_UPCOMING_SUPPLY_START_DUAL_GAS_FIRST" ->  Boost,
    "PAYG_ONBOARDING_SSD_AMENDED_DUAL" ->  Boost,
    "cc-payment-taken" ->  Ovo,
    "props-green-electricity-bolton-cancellation" ->  Ovo,
    "canary" ->  Ovo,
    "SmartMeterBookingCancellationBoost" ->  Boost,
    "apollo-password-reset" ->  Ovo,
    "props-foundation-addon-confirmation-with-dd" ->  Ovo,
    "canary-non-customer" ->  Ovo,
    "SmartMeterBookingConfirmationLumo" ->  Lumo,
    "SmartMeterBookingCancellation" ->  Ovo,
    "get-voucher" ->  Ovo,
    "PAYGSmartMeterAvailability2Comm" ->  Boost,
    "orion-email-changed-old" ->  Ovo,
    "PAYG_ONBOARDING_SUPPLY_START_DUAL_ELECTRICITY_LAST" ->  Boost,
    "polar-integration-sign-up-success-new-customer" ->  Ovo,
    "PAYG_ONBOARDING_SUPPLY_START_DUAL" ->  Boost,
    "orion-password-changed" ->  Ovo,
    "PAYG_ONBOARDING_SIGN_UP_CONFIRMATION_SET_UP_PASSWORD" ->  Boost,
    "PAYG_ONBOARDING_SIGN_UP_CONFIRMATION_BROKER" ->  Boost,
    "PAYMSmartMeterAvailabilitySMS" ->  Ovo,
    "corgi-homeheat-order-confirmation" ->  Corgi,
    "orion-annual-comm" ->  Ovo,
    "PAYMSmartMeterBookingSMS" ->  Ovo,
    "PAYG_ONBOARDING_UPCOMING_SUPPLY_START_ELECTRICITY" ->  Boost,
    "PAYG_ONBOARDING_UPCOMING_SUPPLY_START_DUAL" ->  Boost,
    "orion-password-reset" ->  Ovo,
    "SimplySmartBooking" ->  Ovo,
    "PAYG_ONBOARDING_SSD_KNOWN_DUAL" ->  Boost,
    "PAYG_ONBOARDING_SSD_AMENDED_ELECTRICITY" ->  Boost,
    "you-have-left-ovo" ->  Ovo,
    "interest-reward-second-year" ->  Ovo,
    "props-green-gas-bolton-confirmation-with-dd" ->  Ovo,
    "PAYG_ONBOARDING_UPCOMING_SUPPLY_START_DUAL_ELECTRICITY_LAST" ->  Boost,
    "Orion statement" ->  Ovo,
    "polar-integration-sign-up-failed" ->  Ovo,
    "props-green-electricity-bolton-confirmation-without-dd" ->  Ovo,
    "PAYMSmartMeterAvailabilityComm" ->  Ovo,
    "SmartMeterBookingCancellationBoost.zip" ->  Boost,
    "SmartMeterBookingCancellationLumo" ->  Lumo,
    "props-foundation-addon-cancellation" ->  Ovo,
    "props-green-bolton-confirmation-with-dd" ->  Ovo,
    "PAYG_ONBOARDING_UPCOMING_SUPPLY_START_DUAL_ELECTRICITY_FIRST" ->  Boost,
    "get-link" ->  Ovo,
    "LumoSmartMeterAvailability2Comm" ->  Lumo,
    "LumoSmartMeterAvailabilityComm" ->  Lumo,
    "PAYG_ONBOARDING_SUPPLY_START_DUAL_ELECTRICITY_FIRST" ->  Boost,
    "SmartMeterBookingConfirmationBoost" ->  Boost,
    "polar-integration-sign-up-success" ->  Ovo,
    "props-green-bolton-confirmation-without-dd" ->  Ovo,
    "sprint-review-demo" ->  Ovo,
    "payg-credit-transfer-dual" ->  Boost,
    "PAYG_ONBOARDING_SSD_AMENDED_DUAL_GAS" ->  Boost,
    "props-foundation-addon-confirmation-without-dd" ->  Ovo,
    "PAYG_ONBOARDING_SIGN_UP_CONFIRMATION" ->  Boost,
    "interest-reward-third-year" ->  Ovo,
    "props-green-gas-bolton-confirmation-without-dd" ->  Ovo,
    "PAYG_ONBOARDING_SUPPLY_START_ELECTRICITY" ->  Boost,
    "PAYG_ONBOARDING_UPCOMING_SUPPLY_START_DUAL_GAS_LAST" ->  Boost,
    "ONBOARDING_SIGN_UP_CONFIRMATION_BROKER" ->  Boost,
    "props-green-electricity-bolton-confirmation" ->  Ovo,
    "interest-reward-first-year" ->  Ovo,
    "props-green-gas-bolton-cancellation" ->  Ovo,
    "PAYMSmartMeterAvailability2Comm" ->  Ovo
  )

  createNewTemplateSummariesTable()
}
