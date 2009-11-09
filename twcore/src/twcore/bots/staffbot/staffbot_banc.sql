/*
SQLyog Community Edition- MySQL GUI v8.03 
MySQL - 5.1.32-community : Database - twcore
*********************************************************************
*/


/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/`twcore` /*!40100 DEFAULT CHARACTER SET latin1 */;

/*Table structure for table `tblbanc` */

CREATE TABLE `tblbanc` (
  `fnID` int(11) NOT NULL AUTO_INCREMENT,
  `fcType` varchar(7) NOT NULL,
  `fcUsername` varchar(255) NOT NULL,
  `fcIP` varchar(15) DEFAULT NULL COMMENT 'IP is NULL if it couldn''t be found in alias table',
  `fcMID` varchar(10) DEFAULT NULL COMMENT 'MID is NULL if it couldn''t be found in alias table',
  `fcMinAccess` varchar(5) NOT NULL,
  `fnDuration` int(11) DEFAULT NULL COMMENT 'Time in minutes, if NULL then indefinite',
  `fcStaffer` varchar(255) NOT NULL,
  `fcComment` varchar(512) DEFAULT NULL,
  `fbNotification` tinyint(1) NOT NULL DEFAULT '1',
  `fdCreated` datetime NOT NULL,
  `ftModified` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`fnID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;