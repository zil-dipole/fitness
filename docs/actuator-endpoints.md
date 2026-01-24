# Actuator Endpoints

Spring Boot Actuator provides several production-ready endpoints to monitor and manage the application. The following endpoints are exposed in this application:

## Enabled Endpoints

- `/actuator/health` - Shows application health information
- `/actuator/info` - Displays arbitrary application info
- `/actuator/metrics` - Shows metrics information for the current application
- `/actuator/env` - Exposes properties from Spring's ConfigurableEnvironment
- `/actuator/beans` - Displays a complete list of all the Spring beans in your application
- `/actuator/conditions` - Shows the conditions that were evaluated on configuration and auto-configuration classes
- `/actuator/mappings` - Displays a list of all @RequestMapping paths
- `/actuator/startup` - Shows the startup steps performed by your application

## Health Endpoint

The health endpoint provides detailed information about the application's health status. It includes information about:

- Database connectivity status
- Redis connectivity status
- Overall application status (UP/DOWN)

## Accessing Endpoints

To access these endpoints, start the application and make HTTP requests to:

```
http://localhost:8080/actuator/{endpoint-name}
```

For example:
```
curl http://localhost:8080/actuator/health
```

## Security Note

In a production environment, you should consider securing these endpoints with appropriate authentication and authorization mechanisms, as they can expose sensitive information about your application.
