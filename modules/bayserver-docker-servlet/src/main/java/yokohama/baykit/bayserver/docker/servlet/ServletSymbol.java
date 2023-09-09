package yokohama.baykit.bayserver.docker.servlet;

public enum ServletSymbol {

    // Servlet Errors
    SVT_INVALID_PARAMETER,
    SVT_INVALID_LOCATION,
    SVT_LOCATION_NOT_FOUND,
    SVT_WEBXML_PARSE_ERROR,
    SVT_PATH_MUST_STARTS_WITH_SLASH,
    SVT_COULD_NOT_INITIALIZE_JASPER,
    SVT_SERVLET_NOT_FOUND,
    SVT_FILTER_NOT_FOUND,
    SVT_UNKNOWN_RESOURCE_TYPE,
    SVT_RESOURCE_TYPE_NOT_SPECIFIED,
    SVT_CANNOT_CREATE_MAIL_SESSION,
    SVT_CANNOT_CREATE_DATASOURCE,
    SVT_CONTEXT_INITIALIZE_ERROR,
}