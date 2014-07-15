/*
 * Copyright 2012 JBoss Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.drools.workbench.screens.dtablexls.backend.server.conversion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.decisiontable.parser.ActionType;
import org.drools.decisiontable.parser.ActionType.Code;
import org.drools.decisiontable.parser.DefaultRuleSheetListener;
import org.drools.decisiontable.parser.RuleSheetListener;
import org.drools.decisiontable.parser.RuleSheetParserUtil;
import org.drools.decisiontable.parser.SourceBuilder;
import org.drools.decisiontable.parser.xls.PropertiesSheetListener;
import org.drools.decisiontable.parser.xls.PropertiesSheetListener.CaseInsensitiveMap;
import org.drools.workbench.models.guided.dtable.shared.conversion.ConversionMessageType;
import org.drools.workbench.models.guided.dtable.shared.conversion.ConversionResult;
import org.drools.workbench.models.guided.dtable.shared.model.GuidedDecisionTable52;
import org.drools.template.model.Global;
import org.drools.template.model.Import;
import org.drools.template.model.Package;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.DefaultDescriptionBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableActivationGroupBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableAgendaGroupBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableAutoFocusBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableCalendarsBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableDescriptionBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableDurationBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableLHSBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableLockonActiveBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableMetadataBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableNameBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableNoLoopBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableRHSBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableRuleflowGroupBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableSalienceBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableSourceBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableTimerBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.HasColumnHeadings;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.ParameterUtilities;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.RowNumberBuilder;

public class GuidedDecisionTableGeneratorListener
        implements
        RuleSheetListener {

    //keywords
    private static final int ACTION_ROW = 1;
    private static final int OBJECT_TYPE_ROW = 2;
    private static final int CODE_ROW = 3;
    private static final int LABEL_ROW = 4;

    //Row Number column must always be at position 0
    private static final int ROW_NUMBER_COLUMN_INDEX = 0;

    //Description column must always be at position 1
    private static final int DESCRIPTION_COLUMN_INDEX = 1;

    private static GuidedDecisionTableSourceBuilder ROW_NUMBER_BUILDER = new RowNumberBuilder();
    private static GuidedDecisionTableSourceBuilder DEFAULT_DESCRIPTION_BUILDER = new DefaultDescriptionBuilder();

    //State machine variables for this parser
    private boolean _isInRuleTable = false;
    private boolean _haveColumnsBeenIdentified = false;
    private int _ruleRow;
    private int _ruleStartColumn;
    private int _ruleEndColumn;
    private int _ruleStartRow;
    private boolean _currentSequentialFlag = false;    // indicates that we are in sequential mode
    private boolean _currentEscapeQuotesFlag = true;   // indicates that we are escaping quotes
    private GuidedDecisionTable52 _dtable;
    private boolean _isNewDataRow = false;

    //Accumulated output
    private Map<Integer, ActionType> _actions;
    private final List<GuidedDecisionTable52> _dtables = new ArrayList<GuidedDecisionTable52>();
    private List<GuidedDecisionTableSourceBuilder> _sourceBuilders;

    //RuleSet wide configuration
    private final PropertiesSheetListener _propertiesListener = new PropertiesSheetListener();

    //Results of conversion
    private ConversionResult _conversionResult;

    private ParameterUtilities _parameterUtilities;

    public GuidedDecisionTableGeneratorListener( ConversionResult conversionResult ) {
        this._conversionResult = conversionResult;
    }

    public CaseInsensitiveMap getProperties() {
        return this._propertiesListener.getProperties();
    }

    public Package getRuleSet() {
        throw new UnsupportedOperationException( "Use getGuidedDecisionTables() instead." );
    }

    public List<Import> getImports() {
        return RuleSheetParserUtil.getImportList( getProperties().getProperty( DefaultRuleSheetListener.IMPORT_TAG ) );
    }

    public List<Global> getGlobals() {
        return RuleSheetParserUtil.getVariableList( getProperties().getProperty( DefaultRuleSheetListener.VARIABLES_TAG ) );
    }

    public List<String> getFunctions() {
        return getProperties().getProperty( DefaultRuleSheetListener.FUNCTIONS_TAG );
    }

    public List<String> getQueries() {
        return getProperties().getProperty( DefaultRuleSheetListener.QUERIES_TAG );
    }

    public List<String> getTypeDeclarations() {
        return getProperties().getProperty( DefaultRuleSheetListener.DECLARES_TAG );
    }

    public List<GuidedDecisionTable52> getGuidedDecisionTables() {
        return _dtables;
    }

    public void startSheet( final String name ) {
    }

    public void finishSheet() {
        this._propertiesListener.finishSheet();
        finishRuleTable();
    }

    public void newRow( final int rowNumber,
                        final int columns ) {
        if ( rowNumber > this._ruleStartRow + LABEL_ROW ) {
            this._isNewDataRow = true;
        }
    }

    public void newCell( final int row,
                         final int column,
                         final String value,
                         int mergedColStart ) {
        //Ignore empty cells unless we've identified all columns
        if ( isCellValueEmpty( value ) && !this._haveColumnsBeenIdentified ) {
            return;
        }
        //Ignore cells beyond the extent of the defined columns
        if ( column > this._ruleEndColumn && this._haveColumnsBeenIdentified ) {
            return;
        }
        if ( _isInRuleTable && row == this._ruleStartRow ) {
            return;
        }
        if ( this._isInRuleTable ) {
            processRuleCell( row,
                             column,
                             value,
                             mergedColStart );
        } else {
            processNonRuleCell( row,
                                column,
                                value );
        }
    }

    /**
     * This gets called each time a "new" rule table is found.
     */
    private void initRuleTable( final int row,
                                final int column,
                                final String value ) {
        preInitRuleTable( row,
                          column,
                          value );
        this._isInRuleTable = true;
        this._actions = new HashMap<Integer, ActionType>();
        this._ruleStartColumn = column;
        this._ruleEndColumn = column;
        this._ruleStartRow = row;
        this._ruleRow = row + LABEL_ROW + 1;
        this._isNewDataRow = false;

        // setup stuff for the rules to come.. (the order of these steps are important !)
        this._currentSequentialFlag = getSequentialFlag();
        this._currentEscapeQuotesFlag = getEscapeQuotesFlag();
        this._parameterUtilities = new ParameterUtilities();

        //Setup new Decision Table
        this._dtable = new GuidedDecisionTable52();
        this._dtable.setTableFormat( GuidedDecisionTable52.TableFormat.EXTENDED_ENTRY );
        this._dtable.setTableName( RuleSheetParserUtil.getRuleName( value ) );
        this._sourceBuilders = new ArrayList<GuidedDecisionTableSourceBuilder>();
        ROW_NUMBER_BUILDER.clearValues();
        DEFAULT_DESCRIPTION_BUILDER.clearValues();
        this._sourceBuilders.add( ROW_NUMBER_COLUMN_INDEX,
                                  ROW_NUMBER_BUILDER );
        this._sourceBuilders.add( DESCRIPTION_COLUMN_INDEX,
                                  DEFAULT_DESCRIPTION_BUILDER );

        postInitRuleTable( row,
                           column,
                           value );
    }

    private void finishRuleTable() {
        if ( this._isInRuleTable ) {
            populateDecisionTable();
            this._dtables.add( this._dtable );
            this._currentSequentialFlag = false;
            this._isInRuleTable = false;
            this._haveColumnsBeenIdentified = false;
            this._isNewDataRow = false;
        }
    }

    private void populateDecisionTable() {
        for ( GuidedDecisionTableSourceBuilder sb : this._sourceBuilders ) {
            sb.populateDecisionTable( this._dtable );
        }
    }

    /**
     * Called before rule table initialisation. Subclasses may override this
     * method to do additional processing.
     */
    protected void preInitRuleTable( int row,
                                     int column,
                                     String value ) {
    }

    /**
     * Called after rule table initialisation. Subclasses may override this
     * method to do additional processing.
     */
    protected void postInitRuleTable( int row,
                                      int column,
                                      String value ) {
    }

    private boolean getSequentialFlag() {
        final String seqFlag = getProperties().getSingleProperty( DefaultRuleSheetListener.SEQUENTIAL_FLAG,
                                                                  "false" );
        return RuleSheetParserUtil.isStringMeaningTrue( seqFlag );
    }

    private boolean getEscapeQuotesFlag() {
        final String escFlag = getProperties().getSingleProperty( DefaultRuleSheetListener.ESCAPE_QUOTES_FLAG,
                                                                  "true" );
        return RuleSheetParserUtil.isStringMeaningTrue( escFlag );
    }

    private void processNonRuleCell( final int row,
                                     final int column,
                                     final String value ) {
        String testVal = value.trim().toLowerCase();
        if ( testVal.startsWith( DefaultRuleSheetListener.RULE_TABLE_TAG ) ) {
            initRuleTable( row,
                           column,
                           value.trim() );
        } else {
            this._propertiesListener.newCell( row,
                                              column,
                                              value,
                                              RuleSheetListener.NON_MERGED );
        }
    }

    private void processRuleCell( final int row,
                                  final int column,
                                  final String value,
                                  final int mergedColStart ) {
        String trimVal = value.trim();
        String testVal = trimVal.toLowerCase();
        if ( testVal.startsWith( DefaultRuleSheetListener.RULE_TABLE_TAG ) ) {
            finishRuleTable();
            initRuleTable( row,
                           column,
                           trimVal );
            return;
        }

        // Ignore any comments cells preceding the first rule table column
        if ( column < this._ruleStartColumn ) {
            return;
        }

        // Ignore any further cells from the rule def row
        if ( row == this._ruleStartRow ) {
            return;
        }

        switch ( row - this._ruleStartRow ) {
            case ACTION_ROW:
                //CONDITION, ACTION, ATTRIBUTE etc...
                this._ruleEndColumn = column;
                doActionTypeCell( row,
                                  column,
                                  trimVal );
                break;

            case OBJECT_TYPE_ROW:
                //Pattern definition ("Driver", "Smurf" etc...)
                this._haveColumnsBeenIdentified = true;
                doObjectTypeCell( row,
                                  column,
                                  trimVal,
                                  mergedColStart );
                break;

            case CODE_ROW:
                //Column definition ("age > $1" etc...)
                doCodeCell( row,
                            column,
                            trimVal );
                break;

            case LABEL_ROW:
                //Decision Table Column header
                doLabelCell( row,
                             column,
                             trimVal );
                break;

            default:
                if ( this._isNewDataRow ) {
                    this._isNewDataRow = false;
                    ROW_NUMBER_BUILDER.addCellValue( row,
                                                     0,
                                                     "" );
                    DEFAULT_DESCRIPTION_BUILDER.addCellValue( row,
                                                              1,
                                                              "" );
                }
                doDataCell( row,
                            column,
                            trimVal );
                break;
        }
    }

    private void doActionTypeCell( final int row,
                                   final int column,
                                   final String trimVal ) {
        ActionType.addNewActionType( this._actions,
                                     trimVal,
                                     column,
                                     row );
        final ActionType actionType = getActionForColumn( row,
                                                          column );
        GuidedDecisionTableSourceBuilder sb = null;
        switch ( actionType.getCode() ) {
            case CONDITION:
                //SourceBuilders for CONDITIONs are set when processing the Object row

            case ACTION:
                //SourceBuilders for ACTIONs are set when processing the Object row
                break;

            case NAME:
                sb = new GuidedDecisionTableNameBuilder( row - 1,
                                                         column,
                                                         this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case DESCRIPTION:
                //Remove default Description Column builder and add that provided
                this._sourceBuilders.remove( DEFAULT_DESCRIPTION_BUILDER );
                sb = new GuidedDecisionTableDescriptionBuilder( row - 1,
                                                                column,
                                                                this._conversionResult );

                //Description column must always be at position 1
                this._sourceBuilders.add( DESCRIPTION_COLUMN_INDEX,
                                          sb );
                actionType.setSourceBuilder( sb );
                break;

            case SALIENCE:
                sb = new GuidedDecisionTableSalienceBuilder( row - 1,
                                                             column,
                                                             this._currentSequentialFlag,
                                                             this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case DURATION:
                sb = new GuidedDecisionTableDurationBuilder( row - 1,
                                                             column,
                                                             this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case TIMER:
                sb = new GuidedDecisionTableTimerBuilder( row - 1,
                                                          column,
                                                          this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case CALENDARS:
                sb = new GuidedDecisionTableCalendarsBuilder( row - 1,
                                                              column,
                                                              this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case NOLOOP:
                sb = new GuidedDecisionTableNoLoopBuilder( row - 1,
                                                           column,
                                                           this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case LOCKONACTIVE:
                sb = new GuidedDecisionTableLockonActiveBuilder( row - 1,
                                                                 column,
                                                                 this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case AUTOFOCUS:
                sb = new GuidedDecisionTableAutoFocusBuilder( row - 1,
                                                              column,
                                                              this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case ACTIVATIONGROUP:
                sb = new GuidedDecisionTableActivationGroupBuilder( row - 1,
                                                                    column,
                                                                    this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case AGENDAGROUP:
                sb = new GuidedDecisionTableAgendaGroupBuilder( row - 1,
                                                                column,
                                                                this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case RULEFLOWGROUP:
                sb = new GuidedDecisionTableRuleflowGroupBuilder( row - 1,
                                                                  column,
                                                                  this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;

            case METADATA:
                sb = new GuidedDecisionTableMetadataBuilder( row - 1,
                                                             column,
                                                             this._conversionResult );
                actionType.setSourceBuilder( sb );
                this._sourceBuilders.add( sb );
                break;
        }

    }

    private void doObjectTypeCell( final int row,
                                   final int column,
                                   final String value,
                                   final int mergedColStart ) {
        if ( value.indexOf( "$param" ) > -1 || value.indexOf( "$1" ) > -1 ) {
            final String message = "It looks like you have snippets in the row that is " +
                    "meant for object declarations." + " Please insert an additional row before the snippets, " +
                    "at cell " + RuleSheetParserUtil.rc2name( row,
                                                              column );
            this._conversionResult.addMessage( message,
                                               ConversionMessageType.ERROR );
        }

        ActionType actionType = getActionForColumn( row,
                                                    column );
        if ( mergedColStart == RuleSheetListener.NON_MERGED ) {
            if ( actionType.getCode() == Code.CONDITION ) {
                GuidedDecisionTableSourceBuilder sb = new GuidedDecisionTableLHSBuilder( row - 1,
                                                                                         column,
                                                                                         value,
                                                                                         this._parameterUtilities,
                                                                                         this._conversionResult );
                this._sourceBuilders.add( sb );
                actionType.setSourceBuilder( sb );

            } else if ( actionType.getCode() == Code.ACTION ) {
                GuidedDecisionTableSourceBuilder sb = new GuidedDecisionTableRHSBuilder( row - 1,
                                                                                         column,
                                                                                         value,
                                                                                         this._parameterUtilities,
                                                                                         this._conversionResult );
                this._sourceBuilders.add( sb );
                actionType.setSourceBuilder( sb );
            }

        } else {
            if ( column == mergedColStart ) {
                if ( actionType.getCode() == Code.CONDITION ) {
                    GuidedDecisionTableSourceBuilder sb = new GuidedDecisionTableLHSBuilder( row - 1,
                                                                                             column,
                                                                                             value,
                                                                                             this._parameterUtilities,
                                                                                             this._conversionResult );
                    this._sourceBuilders.add( sb );
                    actionType.setSourceBuilder( sb );

                } else if ( actionType.getCode() == Code.ACTION ) {
                    GuidedDecisionTableSourceBuilder sb = new GuidedDecisionTableRHSBuilder( row - 1,
                                                                                             column,
                                                                                             value,
                                                                                             this._parameterUtilities,
                                                                                             this._conversionResult );
                    this._sourceBuilders.add( sb );
                    actionType.setSourceBuilder( sb );

                }
            } else {
                ActionType startOfMergeAction = getActionForColumn( row,
                                                                    mergedColStart );
                actionType.setSourceBuilder( startOfMergeAction.getSourceBuilder() );
            }
        }

    }

    private void doCodeCell( final int row,
                             final int column,
                             final String value ) {

        final ActionType actionType = getActionForColumn( row,
                                                          column );
        if ( value.trim().equals( "" ) &&
                ( actionType.getCode() == Code.ACTION ||
                        actionType.getCode() == Code.CONDITION ||
                        actionType.getCode() == Code.METADATA ) ) {
            final String message = "Code description in cell " +
                    RuleSheetParserUtil.rc2name( row,
                                                 column ) +
                    " does not contain any code specification. It should!";
            this._conversionResult.addMessage( message,
                                               ConversionMessageType.ERROR );
        }

        actionType.addTemplate( row,
                                column,
                                value );
    }

    private void doLabelCell( final int row,
                              final int column,
                              final String value ) {
        final ActionType actionType = getActionForColumn( row,
                                                          column );
        SourceBuilder sb = actionType.getSourceBuilder();
        if ( sb instanceof HasColumnHeadings ) {
            ( (HasColumnHeadings) sb ).setColumnHeader( column,
                                                        value );
        }
    }

    private void doDataCell( final int row,
                             final int column,
                             final String value ) {
        final ActionType actionType = getActionForColumn( row,
                                                          column );

        if ( row - this._ruleRow > 1 ) {
            // Encountered a row gap from the last rule. This is not part of the ruleset.
            finishRuleTable();
            processNonRuleCell( row,
                                column,
                                value );
            return;
        }

        if ( row > this._ruleRow ) {
            // In a new row/rule
            this._ruleRow++;
        }

        //Add data to column definition
        actionType.addCellValue( row,
                                 column,
                                 value,
                                 _currentEscapeQuotesFlag );
    }

    private boolean isCellValueEmpty( final String value ) {
        return value == null || "".equals( value.trim() );
    }

    private ActionType getActionForColumn( final int row,
                                           final int column ) {
        final ActionType actionType = this._actions.get( column );

        if ( actionType == null ) {
            final String message = "Code description in cell " +
                    RuleSheetParserUtil.rc2name( row,
                                                 column ) +
                    " does not have an 'ACTION' or 'CONDITION' column header.";
            this._conversionResult.addMessage( message,
                                               ConversionMessageType.ERROR );
        }

        return actionType;
    }

}
