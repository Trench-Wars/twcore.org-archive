-- phpMyAdmin SQL Dump
-- version 2.11.0
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Oct 23, 2009 at 02:49 PM
-- Server version: 5.0.45
-- PHP Version: 5.2.8

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";

--
-- Database: `trench_TrenchWars`
--

-- --------------------------------------------------------

--
-- Table structure for table `tblAlias`
--

CREATE TABLE `tblAlias` (
  `fnAliasID` int(10) NOT NULL auto_increment,
  `fnUserID` int(10) NOT NULL default '0',
  `fcIPString` varchar(15) NOT NULL default '',
  `fnIP` int(12) unsigned NOT NULL default '0',
  `fnMachineID` int(11) NOT NULL default '0',
  `fnTimesUpdated` int(10) NOT NULL default '0',
  `fdRecorded` datetime NOT NULL default '0000-00-00 00:00:00',
  `fdUpdated` datetime NOT NULL default '0000-00-00 00:00:00',
  PRIMARY KEY  (`fnAliasID`),
  KEY `fcIP` (`fcIPString`,`fnMachineID`),
  KEY `fnIP` (`fnIP`),
  KEY `fnUserID` (`fnUserID`),
  KEY `fnIPfnMachineID` (`fnIP`,`fnMachineID`),
  KEY `fdUpdated` (`fdUpdated`),
  KEY `fdRecorded` (`fdRecorded`)
) ENGINE=MyISAM  DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `tblAliasSuppression`
--

CREATE TABLE `tblAliasSuppression` (
  `fnAliasID` int(10) unsigned NOT NULL auto_increment,
  `fnUserID` int(10) unsigned NOT NULL default '0',
  `fcIP` varchar(15) NOT NULL default '',
  `fnMID` int(12) NOT NULL default '0',
  `fnStatus` tinyint(3) unsigned NOT NULL default '0',
  `fdResetTime` datetime default NULL,
  PRIMARY KEY  (`fnAliasID`),
  UNIQUE KEY `fnUserID` (`fnUserID`),
  KEY `fnStatus` (`fnStatus`)
) ENGINE=MyISAM  DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `tblDeleteLog`
--

CREATE TABLE `tblDeleteLog` (
  `fnDeleteLogID` int(11) NOT NULL auto_increment,
  `fcDeleteQuery` varchar(255) NOT NULL default '',
  `ftDone` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`fnDeleteLogID`)
) ENGINE=MyISAM  DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `tblTeam`
--

CREATE TABLE `tblTeam` (
  `fnTeamID` int(10) unsigned NOT NULL auto_increment,
  `fcTeamName` varchar(25) NOT NULL default '',
  `fcDescription` text,
  `fcWebsite` varchar(255) default NULL,
  `fcBannerFileExt` varchar(5) default NULL,
  `fnFrequency` int(11) NOT NULL default '0',
  `fnTWL` tinyint(1) default '0',
  `fdCreated` datetime NOT NULL default '0000-00-00 00:00:00',
  `fdDeleted` datetime default NULL,
  `ftUpdated` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`fnTeamID`),
  KEY `fnTeamID` (`fnTeamID`,`fcTeamName`),
  KEY `fcTeamName` (`fcTeamName`)
) ENGINE=MyISAM  DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `tblTeamUser`
--

CREATE TABLE `tblTeamUser` (
  `fnTeamUserID` int(10) unsigned NOT NULL auto_increment,
  `fnUserID` int(10) unsigned NOT NULL default '0',
  `fnTeamID` int(10) unsigned NOT NULL default '0',
  `fnCurrentTeam` tinyint(3) unsigned NOT NULL default '1',
  `fdJoined` datetime default NULL,
  `fdQuit` datetime default NULL,
  `fnApply` tinyint(3) unsigned NOT NULL default '0',
  `fnTWL` tinyint(3) unsigned NOT NULL default '0',
  `ftUpdated` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`fnTeamUserID`),
  KEY `fnTeamUserID` (`fnTeamUserID`,`fnUserID`,`fnTeamID`),
  KEY `fnTeamID` (`fnTeamID`),
  KEY `fnUserID` (`fnUserID`),
  KEY `ftUpdated` (`ftUpdated`),
  KEY `fnApply` (`fnApply`),
  KEY `fnCurrentTeam` (`fnCurrentTeam`),
  KEY `fdQuitftUpdated` (`fdQuit`,`ftUpdated`),
  KEY `fdJoinedftUpdated` (`fdJoined`,`ftUpdated`),
  KEY `fdJoined` (`fdJoined`)
) ENGINE=MyISAM  DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `tblUser`
--

CREATE TABLE `tblUser` (
  `fnUserID` int(10) unsigned NOT NULL auto_increment,
  `fcUserName` varchar(35) NOT NULL default '',
  `fdSignedUp` datetime default NULL,
  `fdDeleted` datetime default NULL,
  `ftUpdated` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`fnUserID`),
  KEY `fcUserName` (`fcUserName`),
  KEY `updated` (`ftUpdated`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- Table structure for table `tblUserAccount`
--

CREATE TABLE `tblUserAccount` (
  `fnUserID` int(10) unsigned NOT NULL auto_increment,
  `fcPassword` varchar(60) NOT NULL default '',
  `fcIP` varchar(50) default NULL,
  `fdLastLoggedIn` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`fnUserID`),
  KEY `fnUserAccountID` (`fnUserID`,`fcPassword`,`fcIP`)
) ENGINE=MyISAM  DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `tblUserRank`
--

CREATE TABLE `tblUserRank` (
  `fnUserRankID` int(10) unsigned NOT NULL auto_increment,
  `fnUserID` int(10) unsigned NOT NULL default '0',
  `fnRankID` int(10) unsigned NOT NULL default '0',
  `ftUpdated` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`fnUserRankID`),
  KEY `fnUserRankID` (`fnUserID`,`fnRankID`)
) ENGINE=MyISAM  DEFAULT CHARSET=latin1;
