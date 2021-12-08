package io.mosip.registration.controller;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.ResourceBundle;

import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.controller.reg.Validations;
import io.mosip.registration.exception.PreConditionCheckException;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.mdm.service.impl.MosipDeviceSpecificationFactory;
import io.mosip.registration.preloader.ClientPreLoader;
import io.mosip.registration.preloader.ClientPreLoaderErrorNotification;
import io.mosip.registration.preloader.ClientPreLoaderNotification;
import io.mosip.registration.service.BaseService;
import io.mosip.registration.service.config.GlobalParamService;
import io.mosip.registration.service.config.JobConfigurationService;
import io.mosip.registration.service.config.LocalConfigService;
import io.mosip.registration.service.login.LoginService;
import io.mosip.registration.util.restclient.ServiceDelegateUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import io.mosip.kernel.clientcrypto.service.impl.ClientCryptoFacade;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.auth.LoginController;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Class for initializing the application
 * 
 * @author Sravya Surampalli
 * @since 1.0.0
 *
 */
@Component
public class ClientApplication extends Application {

	/**
	 * Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(ClientApplication.class);

	private static ApplicationContext applicationContext;
	private static Stage applicationPrimaryStage;
	private static String applicationStartTime;

	@Override
	public void init() throws Exception {
		try { //Do heavy lifting here
			if(ClientPreLoader.errorsFound)
				return;

			applicationStartTime = String.valueOf(Timestamp.valueOf(DateUtils.getUTCCurrentDateTime()));
			System.setProperty("java.net.useSystemProxies", "true");
			System.setProperty("file.encoding", "UTF-8");

			notifyPreloader(new ClientPreLoaderNotification("Creating application context..."));
			applicationContext = new AnnotationConfigApplicationContext(AppConfig.class);
			SessionContext.setApplicationContext(applicationContext);
			notifyPreloader(new ClientPreLoaderNotification("Created application context."));

			setupLanguages();
			notifyPreloader(new ClientPreLoaderNotification("Language setup complete."));

			setupResourceBundleBasedOnDefaultAppLang();

			setupAppProperties();
			notifyPreloader(new ClientPreLoaderNotification("Properties with local preferences loaded."));
			setupResourceBundleBasedOnDefaultAppLang();

			notifyPreloader(new ClientPreLoaderNotification("ENV mosip.hostname : " +
					io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap("mosip.hostname")));
			notifyPreloader(new ClientPreLoaderNotification("client.upgrade.server.url : " +
					io.mosip.registration.context.ApplicationContext.getStringValueFromApplicationMap("client.upgrade.server.url")));

			handleInitialSync();

			notifyPreloader(new ClientPreLoaderNotification("Biometric device scanning started..."));
			discoverDevices();

		} catch (Throwable t) {
			ClientPreLoader.errorsFound = true;
			LOGGER.error("Application Initialization Error", t);
			notifyPreloader(new ClientPreLoaderErrorNotification(t));
		}
	}

	@Override
	public void start(Stage primaryStage) {
		try {
			if(ClientPreLoader.errorsFound)
				return;

			LOGGER.info("Login screen Initialization {}", new SimpleDateFormat(RegistrationConstants.HH_MM_SS).format(System.currentTimeMillis()));

			setPrimaryStage(primaryStage);
			LoginController loginController = applicationContext.getBean(LoginController.class);
			loginController.loadInitialScreen(primaryStage);

			LOGGER.info("Login screen loaded {}", new SimpleDateFormat(RegistrationConstants.HH_MM_SS).format(System.currentTimeMillis()));

		} catch (Exception exception) {
			LOGGER.error("Application Initialization Error {}",new SimpleDateFormat(RegistrationConstants.HH_MM_SS).format(System.currentTimeMillis()),
					exception);
		}
	}


	@Override
	public void stop() {
		try {
			super.stop();
			getClientCryptoFacade().getClientSecurity().closeSecurityInstance();
			LOGGER.info("Closed the Client Security Instance");
		} catch (Exception exception) {
			LOGGER.error("REGISTRATION - APPLICATION INITILIZATION - REGISTRATIONAPPINITILIZATION", APPLICATION_NAME,
					APPLICATION_ID,
					"Application Initilization Error"
							+ new SimpleDateFormat(RegistrationConstants.HH_MM_SS).format(System.currentTimeMillis())
							+ ExceptionUtils.getStackTrace(exception));
		} finally {
			System.exit(0);
		}
	}

	private void discoverDevices() {
		MosipDeviceSpecificationFactory deviceSpecificationFactory = applicationContext.getBean(MosipDeviceSpecificationFactory.class);
		notifyPreloader(new ClientPreLoaderNotification("Scanning port "+
				deviceSpecificationFactory.getPortFrom()+" - "+deviceSpecificationFactory.getPortTo()));
		deviceSpecificationFactory.initializeDeviceMap();
		notifyPreloader(new ClientPreLoaderNotification(deviceSpecificationFactory.getAvailableDeviceInfoMap().size() + " devices discovered."));
	}


	private void handleInitialSync() {
		BaseService baseService = applicationContext.getBean("baseService", BaseService.class);

		if(baseService.isInitialSync())  {
			return;
		}

		ServiceDelegateUtil serviceDelegateUtil = applicationContext.getBean(ServiceDelegateUtil.class);
		LoginService loginService = applicationContext.getBean(LoginService.class);

		notifyPreloader(new ClientPreLoaderNotification("Checking server connectivity..."));
		if(serviceDelegateUtil.isNetworkAvailable()) {
			notifyPreloader(new ClientPreLoaderNotification("Machine is ONLINE."));
			long start = System.currentTimeMillis();
			notifyPreloader(new ClientPreLoaderNotification("Client settings startup sync started..."));
			loginService.initialSync(RegistrationConstants.JOB_TRIGGER_POINT_SYSTEM);
			notifyPreloader(new ClientPreLoaderNotification("Client setting startup sync completed in " +
					((System.currentTimeMillis() - start)/1000) + " seconds." ));
		}
		else {
			notifyPreloader(new ClientPreLoaderNotification("Warning : Machine is OFFLINE"));
		}

		JobConfigurationService jobConfigurationService = applicationContext.getBean(JobConfigurationService.class);
		jobConfigurationService.startScheduler();
		notifyPreloader(new ClientPreLoaderNotification("Job scheduler started."));
	}

	private void setupLanguages() throws PreConditionCheckException {
		BaseService baseService = applicationContext.getBean("baseService", BaseService.class);
		io.mosip.registration.context.ApplicationContext.getInstance().setMandatoryLanguages(baseService.getMandatoryLanguages());
		io.mosip.registration.context.ApplicationContext.getInstance().setOptionalLanguages(baseService.getOptionalLanguages());
	}

	private void setupAppProperties() {
		GlobalParamService globalParamService = applicationContext.getBean(GlobalParamService.class);
		LocalConfigService localConfigService = applicationContext.getBean(LocalConfigService.class);
		Map<String, Object> globalProps = globalParamService.getGlobalParams();
		globalProps.putAll(localConfigService.getLocalConfigurations());
		io.mosip.registration.context.ApplicationContext.setApplicationMap(globalProps);
	}

	protected void setupResourceBundleBasedOnDefaultAppLang() throws RegBaseCheckedException {
		//load all bundles in memory and sets default application language
		io.mosip.registration.context.ApplicationContext.loadResources();

		ResourceBundle messageBundle = io.mosip.registration.context.ApplicationContext.getBundle(
				io.mosip.registration.context.ApplicationContext.applicationLanguage(),
				RegistrationConstants.MESSAGES);
		Validations.setResourceBundle(messageBundle);
		RegistrationUIConstants.setBundle(messageBundle);
	}

	private ClientCryptoFacade getClientCryptoFacade() {
		return applicationContext.getBean(ClientCryptoFacade.class);
	}

	public static ApplicationContext getApplicationContext() {
		return applicationContext;
	}

	public static Stage getPrimaryStage() {
		return applicationPrimaryStage;
	}

	public static void setPrimaryStage(Stage primaryStage) {
		applicationPrimaryStage = primaryStage;
	}
	
	public static String getApplicationStartTime() {
		return applicationStartTime;
	}
}
