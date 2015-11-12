<%-- 
    Document   : Authentication
    Created on : Nov 2, 2015, 12:09:20 AM
    Author     : hmgowda
--%>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title data-i18n="title.authentication_status">Authentication Status</title>
</head>
<body>
<div class="container">
        <div class="nagPanel"><span data-i18n="title.administration">Administration</span> &gt; <span data-i18n="title.authentication_status">Authentication Status</span></div>
	<div id="nameOfPage" class="NameHeader" data-i18n="header.authentication_header">View Authentication Status</div>		
	</div>
        <div class="container">
        		<div class="mainHeader"><span class="authStatus" id="AuthePage" data-i18n="label.get_authentication_status">Get Authentication Status</span></div> <!-- was: "Trust Status Dash Board" -->
			<a id="refreshAuth" href="#" onclick="getAuthenticationStatus(this)" data-toggle="tooltip" title="Refresh to get Authentication Status" style="display: none; float: right"><span class="glyphicon glyphicon-refresh"></span>RefreshAuth</a><br>

<script type="text/javascript" src="Scripts/Authentication.js"></script>
</body>
</html>


