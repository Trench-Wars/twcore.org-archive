-- phpMyAdmin SQL Dump
-- version 3.2.4
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Nov 22, 2010 at 10:22 AM
-- Server version: 5.1.41
-- PHP Version: 5.3.1

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- Database: `trench_trenchwars`
--

-- --------------------------------------------------------

--
-- Table structure for table `tblduel__2ban`
--

CREATE TABLE `tblduel__2ban` (
  `fnBanID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `fnUserID` mediumint(11) NOT NULL DEFAULT '0',
  `fcComment` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`fnBanID`)
) ENGINE=InnoDB  DEFAULT CHARSET=latin1 AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `tblduel__2league`
--

CREATE TABLE `tblduel__2league` (
  `fnDuelLeagueID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `fnSeason` mediumint(9) NOT NULL DEFAULT '1',
  `fnTeamID` mediumint(9) NOT NULL DEFAULT '0',
  `fnUserID` mediumint(9) NOT NULL DEFAULT '0',
  `fnWins` mediumint(9) NOT NULL DEFAULT '0',
  `fnLosses` mediumint(9) NOT NULL DEFAULT '0',
  `fnKills` mediumint(9) NOT NULL DEFAULT '0',
  `fnDeaths` mediumint(9) NOT NULL DEFAULT '0',
  `fnSpawns` mediumint(9) NOT NULL DEFAULT '0',
  `fnSpawned` mediumint(9) NOT NULL DEFAULT '0',
  `fnLagouts` mediumint(9) NOT NULL DEFAULT '0',
  `fnTimePlayed` int(11) NOT NULL DEFAULT '0',
  `fnLeagueTypeID` tinyint(4) NOT NULL DEFAULT '0',
  `fnStatus` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`fnDuelLeagueID`),
  KEY `fnUserID` (`fnLeagueTypeID`),
  KEY `fnSeason` (`fnSeason`)
) ENGINE=InnoDB  DEFAULT CHARSET=latin1 AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `tblduel__2match`
--

CREATE TABLE `tblduel__2match` (
  `fnMatchID` mediumint(11) NOT NULL AUTO_INCREMENT,
  `fnSeason` mediumint(9) NOT NULL DEFAULT '1',
  `fnLeagueTypeID` tinyint(4) NOT NULL DEFAULT '0',
  `fnBoxType` tinyint(1) NOT NULL DEFAULT '1',
  `fnWinnerScore` int(11) NOT NULL DEFAULT '0',
  `fnLoserScore` int(11) NOT NULL DEFAULT '0',
  `fnWinnerTeamID` mediumint(11) NOT NULL DEFAULT '0',
  `fnLoserTeamID` mediumint(11) NOT NULL DEFAULT '0',
  `fnWinner1Ship` smallint(6) NOT NULL DEFAULT '1',
  `fnWinner2Ship` smallint(6) NOT NULL DEFAULT '1',
  `fnLoser1Ship` smallint(6) NOT NULL DEFAULT '1',
  `fnLoser2Ship` smallint(6) NOT NULL DEFAULT '1',
  `fnDeaths` mediumint(2) NOT NULL DEFAULT '5',
  `fnCommentID` tinyint(4) NOT NULL DEFAULT '0',
  `fnDuration` int(11) NOT NULL DEFAULT '0',
  `fdRated` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  `fnWinnerRatingBefore` smallint(6) NOT NULL DEFAULT '0',
  `fnWinnerRatingAfter` smallint(6) NOT NULL DEFAULT '0',
  `fnLoserRatingBefore` smallint(6) NOT NULL DEFAULT '0',
  `fnLoserRatingAfter` smallint(6) NOT NULL DEFAULT '0',
  `ftUpdated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`fnMatchID`),
  KEY `fnSeason` (`fnSeason`),
  KEY `fnLeagueTypeID` (`fnLeagueTypeID`),
  KEY `fnWinnerUserID` (`fnWinnerTeamID`),
  KEY `fnLoserUserID` (`fnLoserTeamID`),
  KEY `fnSeasonfnLeagueTypeID` (`fnSeason`,`fnLeagueTypeID`),
  KEY `ftUpdated` (`ftUpdated`)
) ENGINE=InnoDB  DEFAULT CHARSET=latin1 AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `tblduel__2player`
--

CREATE TABLE `tblduel__2player` (
  `fnDuelID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `fnUserID` int(11) NOT NULL DEFAULT '0',
  `fnGameDeaths` int(4) NOT NULL DEFAULT '5',
  `fnNoCount` tinyint(1) NOT NULL DEFAULT '1',
  `fcIP` varchar(15) NOT NULL DEFAULT '0.0.0.0',
  `fnMID` int(11) NOT NULL DEFAULT '0',
  `fnEnabled` int(1) NOT NULL DEFAULT '1',
  `fnLag` int(10) NOT NULL DEFAULT '0',
  `fnLagCheckCount` bigint(100) NOT NULL DEFAULT '0',
  `ftUpdated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `fdLastPlayed` date NOT NULL DEFAULT '0000-00-00',
  PRIMARY KEY (`fnDuelID`),
  KEY `fnUserID` (`fnUserID`)
) ENGINE=InnoDB  DEFAULT CHARSET=latin1 AUTO_INCREMENT=0 ;

-- --------------------------------------------------------

--
-- Table structure for table `tblduel__2team`
--

CREATE TABLE `tblduel__2team` (
  `fnTeamID` int(10) NOT NULL AUTO_INCREMENT,
  `fnSeason` mediumint(9) NOT NULL DEFAULT '1',
  `fnLeagueTypeID` int(4) NOT NULL DEFAULT '0',
  `fnUser1ID` mediumint(9) DEFAULT NULL,
  `fnUser2ID` mediumint(9) DEFAULT NULL,
  `fnWins` mediumint(9) NOT NULL DEFAULT '0',
  `fnLosses` mediumint(9) NOT NULL DEFAULT '0',
  `fnWinStreak` mediumint(9) NOT NULL DEFAULT '0',
  `fnCurrentWinStreak` mediumint(9) NOT NULL DEFAULT '0',
  `fnLossStreak` mediumint(9) NOT NULL DEFAULT '0',
  `fnCurrentLossStreak` mediumint(9) NOT NULL DEFAULT '0',
  `fnAces` mediumint(9) NOT NULL DEFAULT '0',
  `fnAced` mediumint(9) NOT NULL DEFAULT '0',
  `fnLastWin` mediumint(11) DEFAULT NULL,
  `fnLastLoss` mediumint(11) DEFAULT NULL,
  `fnRating` mediumint(9) NOT NULL DEFAULT '0',
  `fdLastPlayed` date NOT NULL DEFAULT '0000-00-00',
  `ftUpdated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `fnStatus` int(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`fnTeamID`)
) ENGINE=InnoDB  DEFAULT CHARSET=latin1 AUTO_INCREMENT=0 ;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
